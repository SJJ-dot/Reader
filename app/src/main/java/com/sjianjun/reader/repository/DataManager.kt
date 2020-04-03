package com.sjianjun.reader.repository

import com.sjianjun.reader.bean.SearchHistory
import com.sjianjun.reader.bean.SearchResult
import com.sjianjun.reader.test.JavaScriptTest
import com.sjianjun.reader.utils.flowIo
import com.sjianjun.reader.utils.launchGlobal
import com.sjianjun.reader.utils.toBookList
import com.sjianjun.reader.utils.withIo
import kotlinx.coroutines.flow.*

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
                    group.getOrPut("bookTitle:${result.bookTitle}-bookAuthor:${result.bookAuthor}", { mutableListOf() }).add(result)
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

    suspend fun saveSearchResult(searchResult: List<SearchResult>) {
        withIo {
            dao.insertBook(searchResult.toBookList())
        }
    }

}