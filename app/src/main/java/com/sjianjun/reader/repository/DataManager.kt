package com.sjianjun.reader.repository

import com.google.gson.JsonObject
import com.sjianjun.coroutine.flowIo
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.*
import com.sjianjun.reader.http.http
import com.sjianjun.reader.utils.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import sjj.alog.Log
import kotlin.collections.first
import kotlin.math.abs

/**
 * 界面数据从数据库订阅刷新
 */
object DataManager {
    private val dao get() = DbFactory.db.dao()

//    <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<搜索>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
    /**
     * 搜素历史记录
     */
    fun getAllSearchHistory(): Flow<List<SearchHistory>> {
        return dao.getAllSearchHistory()
    }

    suspend fun searchHint(query: String): List<String>? {
        return withIo {
            try {
                val resp = http.get(
                    "https://sp0.baidu.com/5a1Fazu8AA54nxGko9WTAnF6hhy/su", mapOf(
                        "wd" to query,
                        "cb" to "f1",
                    ), encoded = false
                )
                val respJson = Regex("f1\\((.*)\\);").find(resp.body)?.groupValues?.getOrNull(1)
                val jarr = gson.fromJson(respJson, JsonObject::class.java).getAsJsonArray("s")
                return@withIo gson.fromJson<List<String>>(jarr.toString())
            } catch (e: Exception) {
                Log.e("搜索提示加载失败 $e")
                null
            }
        }

    }

    suspend fun searchUrl(url: String): Flow<List<List<SearchResult>>> = withIo {
        val allJavaScript = dao.getAllBookSource().first().filter { it.enable }
        if (allJavaScript.isEmpty()) {
            toast("无可用书源，请导入书源")
            return@withIo emptyFlow<List<List<SearchResult>>>()
        }
        val supportSources = allJavaScript.filter { it.isSupported(url) }
        if (supportSources.isEmpty()) {
            toast("不支持该网站")
            return@withIo emptyFlow<List<List<SearchResult>>>()
        }

        val books = supportSources.map {
            async<SearchResult?> {
                val book = it.getDetails(url)
                if (book != null) {
                    val searchResult = SearchResult()
                    searchResult.bookSource = it
                    searchResult.bookTitle = book.title
                    searchResult.bookAuthor = book.author
                    searchResult.bookCover = book.cover
                    searchResult.bookUrl = book.url
                    searchResult
                } else {
                    null
                }
            }
        }
        val results = books.awaitAll().filterNotNull()
        val group = mutableMapOf<String, MutableList<SearchResult>>()
        results.forEach { result ->
            val list = group.getOrPut(result.key) { mutableListOf() }
            list.add(result)
        }
        return@withIo flowOf(group.values.toList())
    }

    /**
     * 搜索书籍。搜索结果插入数据库。由数据库更新。
     */
    suspend fun search(query: String): Flow<List<List<SearchResult>>> {
        //是否是URL
        if (query.startsWith("http://") || query.startsWith("https://")) {
            return searchUrl(query)
        }
        return withIo {
            dao.insertSearchHistory(SearchHistory(query = query))
            //读取所有脚本。只读取一次，不接受后续更新
            val allJavaScript = dao.getAllBookSource().first().filter { it.enable }
            if (allJavaScript.isEmpty()) {
                toast("无可用书源，请导入书源")
                return@withIo emptyFlow<List<List<SearchResult>>>()
            }
            val group = mutableMapOf<String, MutableList<SearchResult>>()
            allJavaScript.asFlow().flatMapMerge {
                //读取每一个发射项目，搜索。创建异步流，并发收集数据
                flow {
                    try {
                        Log.i("search ${it.id} start")
                        val search = it.search(query)
                        if (search != null) {
                            emit(search)
                            Log.i("search ${it.id} resultNum:${search.size}")
                        }
                    } catch (e: Exception) {
                        Log.e("search error ${it.id}", e)
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

    suspend fun deleteSearchHistory(history: List<SearchHistory>) = withIo {
        dao.deleteSearchHistory(history)
    }

    suspend fun saveSearchResult(searchResult: List<SearchResult>): String = withIo {
        dao.saveSearchResult(searchResult.toBookList())
    }

    suspend fun reloadBookFromNet(book: Book?, javaScript: BookSource? = null) = withIo {

        book ?: return@withIo
        val script =
            javaScript ?: book.bookSource ?: dao.getBookSourceById(book.bookSourceId).firstOrNull()
        try {
            if (script == null) {
                throw MessageException("未找到对应书籍书源")
            }
            book.isLoading = true
            dao.updateBook(book)
            val bookDetails = script.getDetails(book.url)!!
            val chapterList = bookDetails.chapterList!!
            chapterList.forEachIndexed { index, chapter ->
                chapter.bookId = book.id
                chapter.index = index
            }
            book.chapterList = chapterList
            book.isLoading = false
            book.error = null
            //先检查章节内容是否有错
            val record = dao.getReadingRecord(book.title)
            val content = dao.getChapterContent(book.id, record?.chapterIndex ?: -1)
            if (content?.contentError == true) {
                chapterList[content.chapterIndex].content = content
                getChapterContent(chapterList[content.chapterIndex], 1)
            }

            dao.updateBookDetails(bookDetails)
        } catch (e: Throwable) {
            Log.e("${script?.id}加载书籍详情：$book", e)
            book.isLoading = false
            book.error = android.util.Log.getStackTraceString(e)
            dao.updateBook(book)
        }
    }

    suspend fun getAllReadingBook(): Flow<List<Book>> = withIo {
        dao.getAllReadingBook()
    }

    suspend fun deleteBook(book: Book) = withIo {
        dao.deleteBook(book)
    }


    suspend fun deleteBookById(book: Book): Boolean {
        return withIo {
            val readingRecord = dao.getReadingRecord(book.title)
            if (readingRecord?.bookId == book.id) {
                val otherBook = dao.getBookByTitleAndAuthor(book.title).first()
                    .find { it.id != book.id } ?: return@withIo false
                changeReadingRecordBookSource(otherBook)
            }
            dao.deleteBookById(book)
            return@withIo true
        }
    }

    suspend fun getBookById(id: String): Book? = withIo {
        dao.getBookById(id)
    }

    suspend fun getBookBookSourceNum(title: String?): Int = withIo {
        if (title.isNullOrEmpty()) {
            return@withIo 0
        }
        return@withIo dao.getBookBookSourceNum(title)
    }

    fun getReadingBook(title: String?): Flow<Book?> {
        if (title.isNullOrEmpty()) {
            return flowOf(null)
        }
        return dao.getReadingBook(title)
    }


    fun getChapterList(bookId: String): Flow<List<Chapter>> {
        return dao.getChapterListByBookId(bookId)
    }

    fun getLastChapterByBookId(bookId: String): Flow<Chapter?> {
        return dao.getLastChapterByBookId(bookId).flowIo()
    }

    fun getChapterByIndex(bookId: String, index: Int): Chapter? {
        return dao.getChapterByIndex(bookId, index)
    }

    fun getReadingRecord(book: Book): Flow<ReadingRecord?> {
        return dao.getReadingRecordFlow(book.title).flowIo()
    }

    /**
     * 切换正在阅读的书的书源
     */
    suspend fun changeReadingRecordBookSource(book: Book) = withIo {
        val readingRecord = getReadingRecord(book).first()
            ?: ReadingRecord(book.title)
        if (readingRecord.bookId == book.id) {
            return@withIo
        }
        val lastChapter = getLastChapterByBookId(book.id).firstOrNull()
        if (lastChapter == null) {
            //获取最新章节失败，尝试重新加载书籍，如果还是加载失败就算了。不管怎样换源必须成功
            reloadBookFromNet(book)
        }
        val chapter =
            dao.getChapterByIndex(readingRecord.bookId, readingRecord.chapterIndex)
        var readChapter: Chapter? = null
        if (chapter != null) {
            //根据章节名查询。取索引最接近那个 这里将章节名转拼音按相似度排序在模糊搜索的时候更准确
            readChapter = dao.getChapterByTitle(book.id, chapter.title!!)
                .firstOrNull()?.minByOrNull { abs(chapter.index - it.index) }
            if (readChapter == null) {
                readChapter = dao.getChapterLikeName(book.id, "%${chapter.name()}%")
                    .minByOrNull { abs(chapter.index - it.index) }
            }
            if (readChapter == null) {
                readChapter = dao.getChapterByIndex(book.id, chapter.index)
                    ?: dao.getLastChapterByBookId(book.id).first()
            }
        }
        readingRecord.bookId = book.id
        readingRecord.chapterIndex = readChapter?.index ?: readingRecord.chapterIndex
        if (readingRecord.chapterIndex == -1) {
            readingRecord.offest = 0
        }
        setReadingRecord(readingRecord)
    }

    /**
     * -1 local ,0 normal,1 force
     */
    suspend fun getChapterContent(
        chapter: Chapter,
        force: Int = 0
    ): Chapter {
        withIo {
            if (chapter.isLoaded) {
                val chapterContent = dao.getChapterContentFlow(chapter.bookId, chapter.index).first()
                chapter.content = chapterContent
                if (force != 1 && chapter.content != null && chapter.content?.contentError != true) {
                    return@withIo
                }
            }

            if (force == -1) {
                return@withIo
            }

            val js = dao.getBookSourceByBookId(chapter.bookId) ?: return@withIo
            val content = js.getChapterContent(chapter.url)
            if (content.isNullOrBlank()) {
                chapter.content = ChapterContent(chapter.bookId, chapter.index, "章节内容加载失败", true)
            } else {
                var error = false
                if (chapter.content?.contentError == true && chapter.content?.content == content) {
                    error = true
                }
                chapter.content = ChapterContent(chapter.bookId, chapter.index, content, error)
            }
            chapter.isLoaded = true
            dao.insertChapter(chapter, chapter.content!!)
        }
        return chapter
    }

    suspend fun insertChapterContent(chapterContent: ChapterContent) = withIo {
        dao.insertChapterContent(chapterContent)
    }


    suspend fun setReadingRecord(record: ReadingRecord): Long = withIo {
        dao.insertReadingRecord(record)
    }

}