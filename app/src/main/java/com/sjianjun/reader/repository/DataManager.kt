@file:Suppress("BlockingMethodInNonBlockingContext")

package com.sjianjun.reader.repository

import android.content.res.AssetManager.ACCESS_BUFFER
import androidx.room.Query
import com.sjianjun.reader.App
import com.sjianjun.reader.bean.*
import com.sjianjun.reader.http.http
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import sjj.alog.Log
import sjj.novel.util.fromJson
import sjj.novel.util.gson
import java.io.InputStream
import java.net.URLEncoder

/**
 * 界面数据从数据库订阅刷新
 */
object DataManager {
    private val dao = db.dao()

    init {
        launchGlobal {
            checkJavaScriptUpdate({
                var version: InputStream? = null
                val versionInfo = try {
                    version = App.app.assets.open("js/version.json", ACCESS_BUFFER)
                    version.bufferedReader().readText()
                } finally {
                    version?.close()
                }
                versionInfo
            }, {
                var jsInput: InputStream? = null
                try {
                    jsInput = App.app.assets.open("js/$it", ACCESS_BUFFER)
                    jsInput.bufferedReader().readText()
                } finally {
                    jsInput?.close()
                }
            })
        }
    }


    suspend fun reloadBookJavaScript(): Boolean? {
        return checkJavaScriptUpdate({
            http.get(globalConfig.javaScriptBaseUrl + "version.json")
        }, {
            http.get(globalConfig.javaScriptBaseUrl + it)
        })
    }

    private suspend inline fun checkJavaScriptUpdate(
        crossinline versionInfo: () -> String,
        crossinline loadScript: (fileName: String) -> String
    ): Boolean? {
        return withIo {
            val versionJson = versionInfo()
            val info = gson.fromJson<JsVersionInfo>(versionJson) ?: return@withIo false
            if (info.version >= globalConfig.javaScriptVersion) {
                info.versions?.filter {
                    globalConfig.javaScriptVersionMap.getValue(it.fileName).value!! < it.version
                }?.map {
                    async {
                        JavaScript(it.fileName, loadScript(it.fileName), it.version)
                    }
                }?.awaitAll().also {
                    if (it != null) {
                        dao.insertJavaScript(it)
                        globalConfig.javaScriptVersion = info.version
                        it.forEach { script ->
                            globalConfig.javaScriptVersionMap.getValue(script.source)
                                .postValue(script.version)
                        }
                    }
                }
            }
            return@withIo true
        }
    }

    fun getAllJavaScript(): Flow<List<JavaScript>> {
        return dao.getAllJavaScript()
    }

    fun getJavaScript(source: String): Flow<JavaScript?> {
        return dao.getJavaScriptBySource(source)
    }

    fun getJavaScript(bookTitle: String, bookAuthor: String): Flow<List<JavaScript>> {
        return dao.getBookJavaScript(bookTitle, bookAuthor)
    }

    suspend fun deleteJavaScript(script: JavaScript) {
        withIo {
            dao.deleteJavaScript(script)
            globalConfig.javaScriptVersionMap.getValue(script.source).postValue(-1)
        }
    }


    suspend fun insertJavaScript(script: JavaScript) {
        dao.insertJavaScript(script)
    }

    suspend fun updateJavaScript(script: JavaScript) {
        dao.updateJavaScript(script)
    }

    /**
     * 搜素历史记录
     */
    fun getAllSearchHistory(): Flow<List<SearchHistory>> {
        return dao.getAllSearchHistory()
    }

    suspend fun searchHint(query: String): List<String>? {
        return withIo {
            val result = http.post(
                "http://book.easou.com/ta/tsAjax.m",
                mapOf("k" to URLEncoder.encode(query, "utf-8"))
            )
            return@withIo gson.fromJson<List<String>>(result)
        }

    }

    /**
     * 搜索书籍。搜索结果插入数据库。由数据库更新。
     */
    suspend fun search(query: String): Flow<List<List<SearchResult>>>? {
        return withIo {
            dao.insertSearchHistory(SearchHistory(query = query))
            //读取所有脚本。只读取一次，不接受后续更新
            val allJavaScript = dao.getAllJavaScript().firstOrNull()?.filter { it.enable }
            if (allJavaScript.isNullOrEmpty()) {
                return@withIo emptyFlow<List<List<SearchResult>>>()
            }
            val group = mutableMapOf<String, MutableList<SearchResult>>()
            allJavaScript.asFlow().flatMapMerge {
                //读取每一个发射项目，搜索。创建异步流，并发收集数据
                flow<List<SearchResult>> {
                    val search = it.search(query)
                    if (search != null) {
                        emit(search)
                    }
                }
            }.map {
                //数据分组返回
                it.toBookGroup(group)
                group.values.toList()
            }.flowIo()
        }
    }

    suspend fun deleteSearchHistory(history: List<SearchHistory>) {
        withIo {
            dao.deleteSearchHistory(history)
        }
    }

    suspend fun saveSearchResult(searchResult: List<SearchResult>): String? {
        return withIo {
            dao.insertBookAndSaveReadingRecord(searchResult.toBookList())
        }
    }

    /**
     * 如果不是更新起点的书籍。去起点检查一遍更新。作为最新更新的标准
     */
    suspend fun updateOrInsertQiDianBook(bookUrl: String) {
        withIo {
            try {
                val book = dao.getBookByUrl(bookUrl).first() ?: return@withIo
                if (book.source == JS_SOURCE_QI_DIAN) {
                    return@withIo
                }
                var qiDianBook =
                    dao.getBookByTitleAuthorAndSource(book.title, book.author, JS_SOURCE_QI_DIAN)
                        .first()
                if (qiDianBook == null) {
                    val javaScript =
                        dao.getJavaScriptBySource(JS_SOURCE_QI_DIAN).first() ?: return@withIo
                    qiDianBook = javaScript.search(book.title)?.find {
                        it.bookTitle == book.title && it.bookAuthor == book.author
                    }?.toBook()
                    if (qiDianBook != null) {
                        dao.insertBook(qiDianBook)
                    }
                }
                if (qiDianBook != null) {
                    reloadBookFromNet(qiDianBook.url)
                }
            } catch (t: Throwable) {
                Log.e("起点书籍更新失败")
            }
        }
    }

    suspend fun reloadBookFromNet(bookUrl: String): Boolean? {
        return withIo {
            val book = dao.getBookByUrl(bookUrl).first() ?: return@withIo false
            val javaScript = dao.getJavaScriptBySource(book.source).first() ?: return@withIo false
            book.isLoading = true
            dao.updateBook(book)
            try {
                val bookDetails = javaScript.getDetails(book.url)!!
                bookDetails.url = bookUrl

                val chapterList = bookDetails.chapterList!!
                chapterList.forEachIndexed { index, chapter ->
                    chapter.bookUrl = bookUrl
                    chapter.index = index
                }
                book.isLoading = false
                dao.updateBookDetails(bookDetails)
                Log.i(bookDetails)
                return@withIo true
            } catch (e: Throwable) {
                Log.i("${javaScript.source}加载书籍详情：", e)
                book.isLoading = false
                dao.updateBook(book)
                Log.i(book)
                return@withIo false
            }
        }
    }

    fun getAllReadingBook(): Flow<List<Book>> {
        return dao.getAllReadingBook()
    }

    suspend fun deleteBook(book: Book) {
        withIo {
            dao.deleteBook(book)
        }
    }


    suspend fun deleteBookByUrl(book: Book): Boolean? {
        return withIo {
            val readingRecord = dao.getReadingRecord(book.title, book.author)
            if (readingRecord?.bookUrl == book.url) {
                val otherBook = dao.getBookByTitleAndAuthor(book.title, book.author).first()
                    .find { it.url != book.url } ?: return@withIo false
                changeReadingRecordBookSource(otherBook)
            }
            dao.deleteBookByUrl(book)
            return@withIo true
        }
    }

    fun getBookByUrl(url: String): Flow<Book?> {
        return dao.getBookByUrl(url)
    }

    fun getBookAndChapterList(bookUrl: String): Flow<Book?> {
        return dao.getBookByUrl(bookUrl)
            .combine(dao.getChapterListByBookUrl(bookUrl)) { book, chapterList ->
                book?.chapterList = chapterList
                book
            }
    }

    fun getBookByTitleAndAuthor(title: String?, author: String?): Flow<List<Book>> {
        if (title.isNullOrEmpty() || author.isNullOrEmpty()) {
            return emptyFlow()
        }
        return dao.getBookByTitleAndAuthor(title, author)
    }

    fun getReadingBook(title: String?, author: String?): Flow<Book?> {
        Log.e("获取正在阅读的书籍：title:$title author:$author")
        if (title.isNullOrEmpty() || author.isNullOrEmpty()) {
            return emptyFlow()
        }
        return dao.getReadingBook(title, author)
    }


    fun getChapterList(bookUrl: String): Flow<List<Chapter>> {
        return dao.getChapterListByBookUrl(bookUrl)
    }

    fun getLastChapterByBookUrl(bookUrl: String): Flow<Chapter?> {

        return dao.getLastChapterByBookUrl(bookUrl).onEach {
            if (it != null) {
                val book = dao.getBookByUrl(it.bookUrl).first()
                if (book != null && book.source != JS_SOURCE_QI_DIAN) {
                    val qiDianBook = dao.getBookByTitleAuthorAndSource(
                        book.title,
                        book.author,
                        JS_SOURCE_QI_DIAN
                    ).first()
                    if (qiDianBook != null) {
                        val qiDianLastChapter = dao.getLastChapterByBookUrl(qiDianBook.url).first()
                        it.isLastChapter =
                            qiDianLastChapter == null || it.name() == qiDianLastChapter.name()
                    }
                } else {
                    it.isLastChapter = true
                }
            }
        }.flowIo()
    }

    fun getChapterByUrl(url: String): Flow<Chapter?> {
        return dao.getChapterByUrl(url)
    }

    fun getReadingRecord(book: Book): Flow<ReadingRecord?> {
        return dao.getReadingRecordFlow(book.title, book.author)
    }

    /**
     * 切换正在阅读的书的书源
     */
    suspend fun changeReadingRecordBookSource(book: Book) {
        withIo {
            val readingRecord = getReadingRecord(book).first()
                ?: ReadingRecord(book.title, book.author)
            if (readingRecord.bookUrl == book.url) {
                return@withIo
            }
            readingRecord.bookUrl = book.url
            val chapter = getChapterByUrl(readingRecord.chapterUrl).first()
            var readChapter: Chapter? = null
            if (chapter != null) {
                //根据章节名查询。取索引最接近那个
                readChapter = dao.getChapterByTitle(book.url, chapter.title!!)
                    .firstOrNull()?.minBy { chapter.index - it.index }
                if (readChapter == null) {
                    //如果章节名没查到。根据章节名模糊查询
                    readChapter = dao.getChapterByName(book.url, "%${chapter.name()}")
                        .firstOrNull()?.minBy { chapter.index - it.index }
                }
                if (readChapter == null) {
                    readChapter = dao.getChapterByName(book.url, "%${chapter.name()}%")
                        .firstOrNull()?.minBy { chapter.index - it.index }
                }
                if (readChapter == null) {
                    readChapter = dao.getChapterByIndex(book.url, chapter.index).first()
                        ?: dao.getLastChapterByBookUrl(book.url).first()
                }

            }
            readingRecord.chapterUrl = readChapter?.url ?: ""
            if (readingRecord.chapterUrl.isBlank()) {
                readingRecord.offest = 0
            }
            setReadingRecord(readingRecord)
        }
    }

    suspend fun getChapterContent(chapter: Chapter, async: Boolean = false): Chapter {
        withIo {
            if (chapter.isLoaded) {
                val chapterContent = dao.getChapterContent(chapter.url).first()
                chapter.content = chapterContent
                if (chapter.content != null) {
                    return@withIo
                }
            }
            val deferred = async {
                val book = dao.getBookByUrl(chapter.bookUrl).first()
                val js = dao.getJavaScriptBySource(book?.source ?: return@async).first()
                val content = js?.getChapterContent(chapter.url)
                if (content.isNullOrBlank()) {
                    chapter.content = ChapterContent(chapter.url, chapter.bookUrl, "章节内容加载失败")
                } else {
                    chapter.content = ChapterContent(chapter.url, chapter.bookUrl, content)
                    chapter.isLoaded = true
                    dao.insertChapter(chapter, chapter.content!!)
                }
            }
            if (!async) {
                deferred.join()
            }
        }
        return chapter
    }

    suspend fun setReadingRecord(record: ReadingRecord): Long {
        return dao.insertReadingRecord(record)
    }
}