package com.sjianjun.reader.repository

import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.SearchHistory
import com.sjianjun.reader.bean.SearchResult
import com.sjianjun.reader.test.JavaScriptTest
import com.sjianjun.reader.utils.flowIo
import com.sjianjun.reader.utils.launchGlobal
import com.sjianjun.reader.utils.toBookList
import com.sjianjun.reader.utils.withIo
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
            flowOf(allJavaScript).flatMapMerge {
                //将列表中的数据展开 发送
                it.asFlow()
            }.flatMapMerge {
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
                    group.getOrPut(
                        "bookTitle:${result.bookTitle}-bookAuthor:${result.bookAuthor}",
                        { mutableListOf() }).add(result)
                }
                group.values.toList()
            }.flowIo()
        }
    }

    suspend fun deleteSearchHistory(history: List<SearchHistory>) {
        withIo {
            dao.deleteSearchHistory(history)
        }
    }

    suspend fun saveSearchResult(searchResult: List<SearchResult>): List<Long> {
        return withIo {
            dao.insertBook(searchResult.toBookList())
            val first = searchResult.first()
            dao.getBookByTitleAndAuthor(
                first.bookTitle!!,
                first.bookAuthor!!
            ).firstOrNull()?.map { it.id.toLong() } ?: emptyList()
        }
    }

    suspend fun reloadBookFromNet(bookId: Int): Boolean {

        return withIo {
            val book = dao.getBookById(bookId).firstOrNull() ?: return@withIo false
            Log.e(book)
            val javaScript = dao.getJavaScriptBySource(book.source!!).first()
            val bookDetails = javaScript.getDetails(book.url!!) ?: return@withIo false
            bookDetails.id = bookId
            dao.updateBook(bookDetails)
            val chapterList = bookDetails.chapterList ?: return@withIo false
            chapterList.forEach {
                it.bookId = bookId
            }
            dao.deleteChapterByBookId(bookId)
            dao.insertChapter(chapterList)
            return@withIo true
        }
    }

    suspend fun getAllBook(): Flow<List<Book>> {
        return dao.getAllBook()
    }

    suspend fun getBookById(id: Int): Flow<Book> {
        return withIo {
            dao.getBookById(id).flatMapConcat {
                dao.getChapterListByBookId(id).map { chapterList ->
                    it.chapterList = chapterList
                    it
                }
            }
        }
    }

    suspend fun getBookByTitleAndAuthor(title: String?, author: String?): Flow<List<Book>> {
        if (title.isNullOrEmpty() || author.isNullOrEmpty()) {
            return emptyFlow()
        }
        return withIo {
            dao.getBookByTitleAndAuthor(title, author)
        }
    }


    fun getChapterList(bookId: Int): Flow<List<Chapter>> {
        return dao.getChapterListByBookId(bookId)
    }


}