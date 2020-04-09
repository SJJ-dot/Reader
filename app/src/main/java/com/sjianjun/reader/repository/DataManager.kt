@file:Suppress("BlockingMethodInNonBlockingContext")

package com.sjianjun.reader.repository

import android.content.res.AssetManager.ACCESS_BUFFER
import com.sjianjun.reader.App
import com.sjianjun.reader.bean.*
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import sjj.novel.util.fromJson
import sjj.novel.util.gson
import java.io.InputStream

/**
 * 界面数据从数据库订阅刷新
 */
object DataManager {
    private val dao = db.dao()

    init {
        launchGlobal {
            var version: InputStream? = null
            val versionInfo = try {
                version = App.app.assets.open("js/version.json", ACCESS_BUFFER)
                version.bufferedReader().readText()
            } finally {
                version?.close()
            }
            val info = gson.fromJson<JsVersionInfo>(versionInfo)
            if (info.version > globalConfig.javaScriptVersion) {
                info.files.map {
                    async {
                        var jsInput: InputStream? = null
                        try {
                            jsInput = App.app.assets.open("js/$it", ACCESS_BUFFER)
                            val js = jsInput.bufferedReader().readText()
                            JavaScript(it, js)
                        } finally {
                            jsInput?.close()
                        }
                    }
                }.awaitAll().also {
                    dao.insertJavaScript(it)
                    globalConfig.javaScriptVersion = info.version
                }
            }
        }
    }


    fun getBookJavaScript(bookTitle: String, bookAuthor: String): Flow<List<JavaScript>> {
        return dao.getBookJavaScript(bookTitle, bookAuthor)
    }

    /**
     * 搜素历史记录
     */
    fun getAllSearchHistory(): Flow<List<SearchHistory>> {
        return dao.getAllSearchHistory()
    }

    /**
     * 搜索书籍。搜索结果插入数据库。由数据库更新。
     */
    suspend fun search(query: String): Flow<List<List<SearchResult>>> {
        return withIo {
            dao.insertSearchHistory(SearchHistory(query = query))
            //读取所有脚本。只读取一次，不接受后续更新
            val allJavaScript = dao.getAllJavaScript().firstOrNull()
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

    suspend fun saveSearchResult(searchResult: List<SearchResult>): String {
        return withIo {
            dao.insertBookAndSaveReadingRecord(searchResult.toBookList())
        }
    }

    suspend fun reloadBookFromNet(bookUrl: String): Boolean {
        return withIo {
            val book = dao.getBookByUrl(bookUrl).first() ?: return@withIo false
            val javaScript = dao.getJavaScriptBySource(book.source).first() ?: return@withIo false
            val bookDetails = javaScript.getDetails(book.url) ?: return@withIo false
            bookDetails.url = bookUrl

            val chapterList = bookDetails.chapterList ?: return@withIo false
            chapterList.forEachIndexed { index, chapter ->
                chapter.bookUrl = bookUrl
                chapter.index = index
            }
            dao.updateBookDetails(bookDetails)
            return@withIo true
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


    fun getChapterList(bookUrl: String): Flow<List<Chapter>> {
        return dao.getChapterListByBookUrl(bookUrl)
    }

    fun getLastChapterByBookUrl(bookUrl: String): Flow<Chapter?> {
        return dao.getLastChapterByBookUrl(bookUrl)
    }

    fun getChapterByUrl(url: String): Flow<Chapter?> {
        return dao.getChapterByUrl(url)
    }

    fun getReadingRecord(book: Book): Flow<ReadingRecord?> {
        return dao.getReadingRecordFlow(book.title, book.author)
    }

    suspend fun getChapterContent(chapter: Chapter): Chapter {
        withIo {
            if (chapter.isLoaded) {
                val chapterContent = dao.getChapterContent(chapter.url).first()
                chapter.content = chapterContent
                if (chapter.content != null) {
                    return@withIo
                }
            }
            val book = dao.getBookByUrl(chapter.bookUrl).first()
            val js = dao.getJavaScriptBySource(book?.source ?: return@withIo).first()
            val content = js?.getChapterContent(chapter.url)
            if (content.isNullOrBlank()) {
                chapter.content = ChapterContent(chapter.url, chapter.bookUrl,"章节内容加载失败")
            } else {
                chapter.content = ChapterContent(chapter.url,chapter.bookUrl, content)
                chapter.isLoaded = true
                dao.insertChapter(chapter, chapter.content!!)
            }
        }
        return chapter
    }

    suspend fun setReadingRecord(record: ReadingRecord): Long {
        return dao.insertReadingRecord(record)
    }

}