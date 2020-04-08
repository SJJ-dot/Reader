package com.sjianjun.reader.repository

import com.sjianjun.reader.bean.*
import com.sjianjun.reader.test.JavaScriptTest
import com.sjianjun.reader.utils.*
import kotlinx.coroutines.flow.*
import sjj.alog.Log

/**
 * 界面数据从数据库订阅刷新
 */
object DataManager {
    private val dao = db.dao()

    init {
        launchGlobal {
            dao.insertJavaScript(listOf(JavaScriptTest.javaScript))
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

    suspend fun saveSearchResult(searchResult: List<SearchResult>): Long {
        return withIo {
            Log.e("insert SearchResult")
            val id = dao.insertBookAndSaveReadingRecord(searchResult.toBookList()).toLong()
            Log.e("insert SearchResult2")
            id
        }
    }

    suspend fun reloadBookFromNet(bookId: Int): Boolean {
        return withIo {
            val book = dao.getBookById(bookId).first() ?: return@withIo false
            val javaScript = dao.getJavaScriptBySource(book.source).first() ?: return@withIo false
            val bookDetails = javaScript.getDetails(book.url!!) ?: return@withIo false
            bookDetails.id = bookId

            val chapterList = bookDetails.chapterList ?: return@withIo false
            chapterList.forEach {
                it.bookId = bookId
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

    fun getBookById(id: Int): Flow<Book?> {
        return dao.getBookById(id).combine(dao.getChapterListByBookId(id)) { book, chapterList ->
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


    fun getChapterList(bookId: Int): Flow<List<Chapter>> {
        return dao.getChapterListByBookId(bookId)
    }

    fun getLastChapterByBookId(bookId: Int): Flow<Chapter?> {
        return dao.getLastChapterByBookId(bookId)
    }

    fun getChapterById(chapterId: Int): Flow<Chapter?> {
        return dao.getChapterById(chapterId)
    }

    fun getReadingRecord(book: Book): Flow<ReadingRecord?> {
        return dao.getReadingRecordFlow(book.title, book.author)
    }

    suspend fun getChapterContent(chapter: Chapter) {
        return withIo {
            if (chapter.isLoaded) {
                val chapterDetails = dao.getChapterById(chapter.id).first()
                chapter.content = chapterDetails?.content
                if (chapter.content?.isNotEmpty() == true) {
                    return@withIo
                }
            }
            val book = dao.getBookById(chapter.bookId).first()
            val js = dao.getJavaScriptBySource(book?.source ?: return@withIo).first()
            chapter.content = js?.getChapterContent(chapter.url ?: return@withIo)
            if (chapter.content?.isNotEmpty() == true) {
                chapter.isLoaded = true
                dao.updateChapter(chapter)
            } else {
                chapter.content = "章节内容加载失败"
            }

        }
    }

    suspend fun setReadingRecord(record: ReadingRecord): Long {
        return dao.insertReadingRecord(record)
    }

}