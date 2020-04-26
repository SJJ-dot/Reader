@file:Suppress("BlockingMethodInNonBlockingContext")

package com.sjianjun.reader.repository

import android.content.res.AssetManager.ACCESS_BUFFER
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
import java.util.concurrent.ConcurrentHashMap

/**
 * 界面数据从数据库订阅刷新
 */
object DataManager {
    //用于存储临时数据。在对应页面销毁的时候 销毁
    val pageDataStore = ConcurrentHashMap<String, Page>()

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


    suspend fun reloadBookJavaScript() {
        checkJavaScriptUpdate({
            http.get(globalConfig.javaScriptBaseUrl + "version.json")
        }, {
            http.get(globalConfig.javaScriptBaseUrl + it)
        })
    }

    private suspend inline fun checkJavaScriptUpdate(
        crossinline versionInfo: () -> String,
        crossinline loadScript: (fileName: String) -> String
    ) {
        withIo {
            val versionJson = versionInfo()
            val info = gson.fromJson<JsVersionInfo>(versionJson)!!
            if (info.version >= globalConfig.javaScriptVersion) {
                info.versions?.map {
                    async {
                        val javaScript = dao.getJavaScriptBySource(it.fileName)
                        if (javaScript != null && javaScript.version >= it.version) {
                            javaScript
                        } else {
                            val js = tryBlock { loadScript(it.fileName) }
                            if (js == null) {
                                null
                            } else {
                                JavaScript(
                                    it.fileName,
                                    js,
                                    it.version,
                                    it.starting,
                                    it.priority,
                                    it.supportBookCity
                                )
                            }
                        }
                    }
                }?.awaitAll().also {
                    if (it != null) {
                        dao.insertJavaScript(it.filterNotNull())
                        globalConfig.javaScriptVersion = info.version
                    }
                }
            }

        }
    }

    fun getAllJavaScript(): Flow<List<JavaScript>> {
        return dao.getAllJavaScript()
    }

    fun getAllSupportBookcityJavaScript(): Flow<List<JavaScript>> {
        return dao.getAllSupportBookcityJavaScript()
    }

    suspend fun getJavaScript(source: String): JavaScript? {
        return withIo { dao.getJavaScriptBySource(source) }
    }

    fun getJavaScript(bookTitle: String, bookAuthor: String): Flow<List<JavaScript>> {
        return dao.getBookJavaScript(bookTitle, bookAuthor)
    }

    suspend fun deleteJavaScript(script: JavaScript) {
        withIo {
            dao.deleteJavaScript(script)
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
            try {
                val result = http.post(
                    "http://book.easou.com/ta/tsAjax.m",
                    mapOf("k" to URLEncoder.encode(query, "utf-8"))
                )
                return@withIo gson.fromJson<List<String>>(result)
            } catch (e: Exception) {
                Log.e("搜索提示加载失败")
                null
            }
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
                it.forEach { result ->
                    val list = group.getOrPut(result.key) { mutableListOf() }
                    list.add(result)
                }
                group.values.sortedByDescending { list -> list.size }
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
    suspend fun updateOrInsertStarting(bookUrl: String) {
        withIo {
            try {
                val book = dao.getBookByUrl(bookUrl).first() ?: return@withIo
                val bookSource = dao.getJavaScriptBySource(book.source)
                if (bookSource?.isStartingStation == true) {
                    return@withIo
                }
                val record = dao.getReadingRecord(book.title, book.author) ?: return@withIo
                if (book.source == record.startingStationBookSource) {
                    return@withIo
                }
                if (record.startingStationBookSource.isBlank()) {
                    //到官方的网站查询并把书籍插入到本地数据库
                    val startingBook = dao.getAllStartingJavaScript().map {
                        async {
                            var startingBook = dao.getBookByTitleAuthorAndSource(
                                book.title,
                                book.author,
                                it.source
                            ).first()
                            if (startingBook == null) {
                                startingBook = it.search(book.title)?.find { result ->
                                    result.bookTitle == book.title && result.bookAuthor == book.author
                                }?.toBook()
                            }
                            startingBook
                        }
                    }.find { it.await() != null }?.await() ?: return@withIo
                    try {
                        dao.insertBook(startingBook)
                    } catch (error: Throwable) {
                        //nothing to do
                    }
                    record.startingStationBookSource = startingBook.source
                    dao.insertReadingRecord(record)
                }
                val startBook = dao.getBookByTitleAuthorAndSource(
                    book.title,
                    book.author,
                    record.startingStationBookSource
                ).first() ?: return@withIo
                Log.i("首发站点书籍：$startBook")
                reloadBookFromNet(startBook.url)
            } catch (t: Throwable) {
                Log.e("首发站点书籍更新失败")
            }
        }
    }

    suspend fun reloadBookFromNet(bookUrl: String): Throwable? {
        return withIo {
            val book = dao.getBookByUrl(bookUrl).first() ?: return@withIo MessageException("书籍查找失败")
            val javaScript = dao.getJavaScriptBySource(book.source)
                ?: return@withIo MessageException("未找到对应书籍书源")
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
                return@withIo null
            } catch (e: Throwable) {
                Log.i("${javaScript.source}加载书籍详情：$book", e)
                book.isLoading = false
                dao.updateBook(book)
                return@withIo e
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

    fun getBookByTitleAndAuthor(title: String?, author: String?): Flow<List<Book>> {
        if (title.isNullOrEmpty() || author.isNullOrEmpty()) {
            return emptyFlow()
        }
        return dao.getBookByTitleAndAuthor(title, author)
    }

    fun getReadingBook(title: String?, author: String?): Flow<Book?> {
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

                if (book != null) {
                    val record = dao.getReadingRecord(book.title, book.author)
                    val source = if (record?.startingStationBookSource.isNullOrBlank())
                        JS_SOURCE_QI_DIAN
                    else
                        record!!.startingStationBookSource
                    val qiDianBook = dao.getBookByTitleAuthorAndSource(
                        book.title,
                        book.author,
                        source
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

    suspend fun getChapterContent(chapter: Chapter, onlyLocal: Boolean): Chapter {
        withIo {
            if (chapter.isLoaded) {
                val chapterContent = dao.getChapterContent(chapter.url).first()
                chapter.content = chapterContent
                if (chapter.content != null) {
                    return@withIo
                }
            }
            if (onlyLocal) {
                return@withIo
            }
            val book = dao.getBookByUrl(chapter.bookUrl).first()
            val js = dao.getJavaScriptBySource(book?.source ?: return@withIo)
            val content = js?.getChapterContent(chapter.url)
            if (content.isNullOrBlank()) {
                chapter.content = ChapterContent(chapter.url, chapter.bookUrl, "章节内容加载失败")
            } else {
                chapter.content = ChapterContent(chapter.url, chapter.bookUrl, content)
                chapter.isLoaded = true
                dao.insertChapter(chapter, chapter.content!!)
            }
        }
        return chapter
    }

    suspend fun setReadingRecord(record: ReadingRecord): Long {
        return dao.insertReadingRecord(record)
    }


    suspend fun loadPage(source: String, script: String = ""): Page? = withIo {
        val js = dao.getJavaScriptBySource(source)
        js?.loadPage(script)
    }


    suspend fun loadBookList(source: String, script: String = ""): List<Book>? = withIo {
        val js = dao.getJavaScriptBySource(source)
        js?.loadBookList(script)
    }
}