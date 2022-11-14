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
                val result = http.post(
                    "https://book.easou.com/ta/tsAjax.m",
                    mapOf("k" to URLEncoder.encode(query, "utf-8"))
                ).body
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
                        val search = it.search(query)
                        if (search != null) {
                            emit(search)
                            Log.i("search ${it.id} resultNum:${search.size}")
                        }
                    } catch (e: Exception) {
                        Log.i("search error ${it.id}", e)
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
        dao.saveSearchResult(searchResult.toBookList()).also {
            WebDavMgr.sync {
                uploadBookInfo()
                uploadReadingRecord()
            }
        }
    }

    suspend fun reloadBookFromNet(book: Book?, javaScript: BookSource? = null) = withIo {

        book ?: return@withIo
        val script = javaScript ?: dao.getBookSourceById(book.bookSourceId).firstOrNull()
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
            dao.updateBookDetails(bookDetails)

            val record = dao.getReadingRecord(book.title, book.author)
            val content = dao.getChapterContent(book.id, record?.chapterIndex ?: -1).firstOrNull()
            if (content?.contentError == true) {
                chapterList[content.chapterIndex].content = content
                getChapterContent(chapterList[content.chapterIndex], 1)
            }
        } catch (e: Throwable) {
            Log.i("${script?.id}加载书籍详情：$book", e)
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
        WebDavMgr.sync {
            uploadBookInfo()
            uploadReadingRecord()
        }
    }


    suspend fun deleteBookById(book: Book): Boolean {
        return withIo {
            val readingRecord = dao.getReadingRecord(book.title, book.author)
            if (readingRecord?.bookId == book.id) {
                val otherBook = dao.getBookByTitleAndAuthor(book.title, book.author).first()
                    .find { it.id != book.id } ?: return@withIo false
                changeReadingRecordBookSource(otherBook)
            }
            dao.deleteBookById(book)
            WebDavMgr.sync { uploadBookInfo() }
            return@withIo true
        }
    }

    suspend fun getBookById(id: String): Book? = withIo {
        dao.getBookById(id)
    }

    suspend fun getBookBookSourceNum(title: String?, author: String?): Int = withIo {
        if (title.isNullOrEmpty() || author.isNullOrEmpty()) {
            return@withIo 0
        }
        return@withIo dao.getBookBookSourceNum(title, author)
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


    fun getChapterList(bookId: String): Flow<List<Chapter>> {
        return dao.getChapterListByBookId(bookId)
    }

    fun getLastChapterByBookId(bookId: String): Flow<Chapter?> {
        return dao.getLastChapterByBookId(bookId).flowIo()
    }

    fun getChapterByIndex(bookId: String, index: Int): Flow<Chapter?> {
        return dao.getChapterByIndex(bookId, index)
    }

    fun getReadingRecord(book: Book): Flow<ReadingRecord?> {
        return dao.getReadingRecordFlow(book.title, book.author)
    }

    /**
     * 切换正在阅读的书的书源
     */
    suspend fun changeReadingRecordBookSource(book: Book) = withIo {
        val readingRecord = getReadingRecord(book).first()
            ?: ReadingRecord(book.title, book.author)
        if (readingRecord.bookId == book.id) {
            return@withIo
        }
        val lastChapter = getLastChapterByBookId(book.id).firstOrNull()
        if (lastChapter == null) {
            //获取最新章节失败，尝试重新加载书籍，如果还是加载失败就算了。不管怎样换源必须成功
            reloadBookFromNet(book)
        }
        val chapter =
            dao.getChapterByIndex(readingRecord.bookId, readingRecord.chapterIndex).first()
        var readChapter: Chapter? = null
        if (chapter != null) {
            //根据章节名查询。取索引最接近那个 这里将章节名转拼音按相似度排序在模糊搜索的时候更准确
            readChapter = dao.getChapterByTitle(book.id, chapter.title!!)
                .firstOrNull()?.minByOrNull { abs(chapter.index - it.index) }
            if (readChapter == null) {
                //如果章节名没查到。根据章节名模糊查询
                readChapter = dao.getChapterLikeName(book.id, "%${chapter.name()}")
                    .firstOrNull()?.minByOrNull { abs(chapter.index - it.index) }
            }
            if (readChapter == null) {
                readChapter = dao.getChapterLikeName(book.id, "%${chapter.name()}%")
                    .firstOrNull()?.minByOrNull { abs(chapter.index - it.index) }
            }
            if (readChapter == null) {
                readChapter = dao.getChapterByIndex(book.id, chapter.index).first()
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
                val chapterContent = dao.getChapterContent(chapter.bookId, chapter.index).first()
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
                var contentError = false
                if (chapter.content?.contentError == true && chapter.content?.content == content) {
                    contentError = true
                }

                chapter.content =
                    ChapterContent(chapter.bookId, chapter.index, content, contentError)
                chapter.isLoaded = true
                dao.insertChapter(chapter, chapter.content!!)
            }
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