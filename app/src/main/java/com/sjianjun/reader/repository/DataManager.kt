@file:Suppress("BlockingMethodInNonBlockingContext")

package com.sjianjun.reader.repository

import com.sjianjun.coroutine.flowIo
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.*
import com.sjianjun.reader.http.http
import com.sjianjun.reader.utils.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import sjj.alog.Log
import java.net.URLEncoder

/**
 * 界面数据从数据库订阅刷新
 */
object DataManager {
    private val dao = db.dao()

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
                    "https://book.easou.com/ta/tsAjax.m",
                    mapOf("k" to URLEncoder.encode(query, "utf-8"))
                )
                return@withIo gson.fromJson<List<String>>(result)
            } catch (e: Exception) {
                Log.i("搜索提示加载失败 $e")
                null
            }
        }

    }

    /**
     * 搜索书籍。搜索结果插入数据库。由数据库更新。
     */
    suspend fun search(query: String): Flow<List<List<SearchResult>>> {
        return withIo {
            dao.insertSearchHistory(SearchHistory(query = query))
            //读取所有脚本。只读取一次，不接受后续更新
            val allJavaScript = JsManager.getAllJs().filter { it.enable }
            if (allJavaScript.isNullOrEmpty()) {
                return@withIo emptyFlow<List<List<SearchResult>>>()
            }
            val group = mutableMapOf<String, MutableList<SearchResult>>()
            allJavaScript.asFlow().flatMapMerge {
                //读取每一个发射项目，搜索。创建异步流，并发收集数据
                flow<List<SearchResult>> {
                    try {
                        val search = it.search(query)
                        if (search != null) {
                            emit(search)
                            Log.i("search ${it.source} resultNum:${search.size}")
                        }
                    } catch (e: Exception) {
                        Log.i("search error ${it.source}", e)
                    }
                }
            }.map {
                //数据分组返回
                it.forEach { result ->
                    val list = group.getOrPut(result.key) { mutableListOf() }
                    list.add(result)
                }
                group.values.sortedWith { a, b ->
                    val aEq = a.first().bookTitle == query
                    val bEq = b.first().bookTitle == query
                    if (aEq || bEq) {
                        when {
                            aEq && bEq -> return@sortedWith 0
                            aEq -> return@sortedWith -1
                            bEq -> return@sortedWith 1
                        }
                    }
                    val aContains = a.first().bookTitle.contains(query, true)
                    val bContains = b.first().bookTitle.contains(query, true)
                    if (aContains || bContains) {
                        when {
                            aContains && bContains -> b.size.compareTo(a.size)
                            aContains -> return@sortedWith -1
                            bContains -> return@sortedWith 1
                        }
                    }
                    b.size.compareTo(a.size)
                }
            }.flowIo()
        }
    }

    suspend fun deleteSearchHistory(history: List<SearchHistory>) {
        withIo {
            dao.deleteSearchHistory(history)
        }
    }

    suspend fun saveSearchResult(searchResult: List<SearchResult>): String? {
        return insertBookAndSaveReadingRecord(searchResult.toBookList())
    }

    suspend fun insertBookAndSaveReadingRecord(bookList: List<Book>): String? {
        return withIo {
            dao.insertBookAndSaveReadingRecord(bookList)
        }
    }

    suspend fun getStartingBook(
        book: Book,
        javaScript: JavaScript? = null,
        onlyLocal: Boolean = false
    ): Book? {

        if (book.source == BOOK_SOURCE_FILE_IMPORT) {
            return null
        }

        return withIo {
            if (javaScript?.isStartingStation == true && javaScript.source == book.source) {
                //可能存在之前本地没有首发站书源，设置为null之后的情况
                if (book.record?.startingStationBookSource?.isEmpty() == true ||
                    book.record?.startingStationBookSource == STARTING_STATION_BOOK_SOURCE_EMPTY
                ) {
                    dao.getReadingRecord(book.title, book.author)?.let {
                        it.startingStationBookSource = javaScript.source
                        dao.insertReadingRecord(it)
                    }
                }
                return@withIo book
            }
            val record = dao.getReadingRecord(book.title, book.author) ?: return@withIo null
            if (book.source == record.startingStationBookSource) {
                return@withIo book
            }

            if (!onlyLocal && record.startingStationBookSource.isBlank()) {
                //到官方的网站查询并把书籍插入到本地数据库
                var error = false
                val startingBook = JsManager.getAllStartingJs().map {
                    async {
                        var startingBook = dao.getBookByTitleAuthorAndSource(
                            book.title,
                            book.author,
                            it.source
                        ).first()
                        if (startingBook == null) {
                            try {
                                startingBook = it.search(book.title)?.find { result ->
                                    result.bookTitle == book.title && result.bookAuthor == book.author
                                }?.toBook()
                            } catch (e: Exception) {
                                Log.e("搜索出错：${it.source} ${book.title}")
                            }
                        }
                        startingBook
                    }
                }.find {
                    try {
                        it.await() != null
                    } catch (e: Exception) {
                        error = true
                        false
                    }
                }?.await()
                if (startingBook == null) {
                    if (!error) {//只有在搜索没有发生错误的时候才保存
                        record.startingStationBookSource = STARTING_STATION_BOOK_SOURCE_EMPTY
                        dao.insertReadingRecord(record)
                    }
                    return@withIo null
                }
                try {
                    dao.insertBook(startingBook)
                } catch (error: Throwable) {
                    //nothing to do
                }
                record.startingStationBookSource = startingBook.source
                dao.insertReadingRecord(record)
            }
            if (record.startingStationBookSource == STARTING_STATION_BOOK_SOURCE_EMPTY) {
                return@withIo null
            }

            return@withIo dao.getBookByTitleAuthorAndSource(
                book.title,
                book.author,
                record.startingStationBookSource
            ).first()

        }
    }

    suspend fun reloadBookFromNet(book: Book?, javaScript: JavaScript? = null) = withIo {

        if (book?.source == BOOK_SOURCE_FILE_IMPORT) {
            return@withIo
        }

        book ?: return@withIo
        val script = javaScript ?: JsManager.getJs(book.source)
        try {
            if (script == null) {
                throw MessageException("未找到对应书籍书源")
            }
            book.isLoading = true
            dao.updateBook(book)

            val bookDetails = script.getDetails(book.url)!!
            bookDetails.url = book.url

            val chapterList = bookDetails.chapterList!!
            chapterList.forEachIndexed { index, chapter ->
                chapter.bookUrl = book.url
                chapter.index = index
            }
            book.isLoading = false
            book.error = null
            dao.updateBookDetails(bookDetails)
        } catch (e: Throwable) {
            Log.i("${script?.source}加载书籍详情：$book", e)
            book.isLoading = false
            book.error = android.util.Log.getStackTraceString(e)
            dao.updateBook(book)
        }
    }

    suspend fun updateBookDetails(book: Book) {
        dao.updateBookDetails(book)
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
                        BOOK_SOURCE_QI_DIAN
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
                    .firstOrNull()?.minByOrNull { chapter.index - it.index }
                if (readChapter == null) {
                    //如果章节名没查到。根据章节名模糊查询
                    readChapter = dao.getChapterByName(book.url, "%${chapter.name()}")
                        .firstOrNull()?.minByOrNull { chapter.index - it.index }
                }
                if (readChapter == null) {
                    readChapter = dao.getChapterByName(book.url, "%${chapter.name()}%")
                        .firstOrNull()?.minByOrNull { chapter.index - it.index }
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

    suspend fun getChapterContent(
        chapter: Chapter,
        onlyLocal: Boolean,
        force: Boolean = false
    ): Chapter {
        withIo {
            if (chapter.isLoaded && !force) {
                val chapterContent = dao.getChapterContent(chapter.url).first()
                chapter.content = chapterContent
                chapter.content?.format()
                if (chapter.content != null) {
                    return@withIo
                }
            }
            if (onlyLocal) {
                return@withIo
            }
            val book = dao.getBookByUrl(chapter.bookUrl).first()

            if (book?.source == BOOK_SOURCE_FILE_IMPORT) {
                return@withIo
            }

            val js = JsManager.getJs(book?.source ?: return@withIo)
            val content = js?.getChapterContent(chapter.url)
            if (content.isNullOrBlank()) {
                chapter.content = ChapterContent(chapter.url, chapter.bookUrl, "章节内容加载失败")
            } else {
                chapter.content = ChapterContent(chapter.url, chapter.bookUrl, content)
                chapter.content?.format()
                chapter.isLoaded = true
                dao.insertChapter(chapter, chapter.content!!)
            }
        }
        return chapter
    }

    suspend fun insertChapterContent(chapterContent: ChapterContent) = withIo {
        dao.insertChapterContent(chapterContent)
    }


    suspend fun setReadingRecord(record: ReadingRecord): Long {
        return dao.insertReadingRecord(record)
    }

}