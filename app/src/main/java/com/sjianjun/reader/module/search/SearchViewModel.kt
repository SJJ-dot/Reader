package com.sjianjun.reader.module.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Transaction
import com.google.gson.JsonObject
import com.sjianjun.coroutine.flowIo
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.bean.SearchHistory
import com.sjianjun.reader.bean.SearchResult
import com.sjianjun.reader.http.http
import com.sjianjun.reader.repository.BookUseCase
import com.sjianjun.reader.repository.DbFactory
import com.sjianjun.reader.utils.gson
import com.sjianjun.reader.utils.key
import com.sjianjun.reader.utils.name
import com.sjianjun.reader.utils.toBookList
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import sjj.alog.Log

class SearchViewModel : ViewModel() {
    private val bookSourceDao get() = DbFactory.db.bookSourceDao()
    private val searchHistoryDao get() = DbFactory.db.searchHistoryDao()
    private val bookDao get() = DbFactory.db.bookDao()
    private val readingRecordDao get() = DbFactory.db.readingRecordDao()

    fun getAllEnableBookSource(): List<BookSource> {
        return bookSourceDao.getAllBookSource().filter { it.enable }
    }

    suspend fun saveSearchResult(searchResult: List<SearchResult>): String = withIo {
        val bookList = searchResult.toBookList()
        DbFactory.db.runInTransaction<String> {
            BookUseCase.cleanDirtyData()
            bookDao.insertBook(bookList)
            val book = bookList.first()
            val readingRecord = readingRecordDao.getReadingRecordSync(book.title)
            Log.i("保存搜索结果记录：$readingRecord $bookList")
            if (bookList.find { it.id == readingRecord?.bookId } == null) {
                readingRecordDao.insertReadingRecord(ReadingRecord(book.title, book.id))
                book.id
            } else {
                readingRecord!!.bookId
            }
        }
    }

    suspend fun searchHint(query: String): List<String>? {
        if (query.isBlank()) {
            return emptyList()
        }
        return withIo {
            try {
                val resp = http.get("https://suggestion.baidu.com/su", mapOf("wd" to "小说 $query"), encoded = false)
                //window.baidu.sug({q:"阵",p:false,s:["阵的拼音","阵问长生","阵组词","阵的笔顺","阵风战斗机","阵雨","阵痛","阵风","阵雨的拼音","阵发性室上性心动过速"]});
                val respJson = Regex("""\((.*)\)""").find(resp.body)?.groupValues?.getOrNull(1)
                val jarr = gson.fromJson(respJson, JsonObject::class.java).getAsJsonArray("s")
                val additional = listOf("笔趣阁", "小说", "TXT下载", "在线阅读", "百度百科", "好看吗", "精校版", "无错版", "下载", "txt", "电视剧", "起点", "全本", "免费阅读", "全文阅读", "最新", "完结", "小说阅读网", "小说阅读器", "小说下载", "小说排行榜", "小说推荐", "小说大全")
                val strings = jarr.map {
                    var text = it.asString.removePrefix("小说").trim().split(" ")[0]
                    while (true) {
                        val string = additional.find { text.contains(it) }
                        if (string == null) {
                            text = text.trim()
                            break
                        } else {
                            text = text.split(string)[0]
                        }
                    }
                    text
                }
                //去重，包括空字符串，包含
                val result = mutableListOf<String>()
                strings.forEach {
                    if (it.isNotBlank() && it !in result) {
                        result.add(it)
                    }
                }
                return@withIo result
            } catch (e: Exception) {
                Log.e("搜索提示加载失败 $e", e)
                null
            }
        }

    }

    suspend fun searchUrl(url: String): Flow<List<List<SearchResult>>> = withIo {
        val allJavaScript = getAllEnableBookSource()
        if (allJavaScript.isEmpty()) {
            toast("无可用书源，请导入书源")
            return@withIo emptyFlow<List<List<SearchResult>>>()
        }
        val supportSources = allJavaScript.filter { it.isSupported(url) }
        Log.i("支持的书源数量：${supportSources.size}，url:$url")
        if (supportSources.isEmpty()) {
            toast("不支持该网站")
            return@withIo emptyFlow<List<List<SearchResult>>>()
        }

        val books = supportSources.map {
            async<SearchResult?> {
                try {
                    val book = it.getDetails(url)!!
                    val searchResult = SearchResult()
                    searchResult.bookSource = it
                    searchResult.bookTitle = book.title
                    searchResult.bookAuthor = book.author
                    searchResult.bookCover = book.cover
                    searchResult.bookUrl = book.url
                    searchResult
                } catch (e: Exception) {
                    Log.e("获取书籍详情失败 ${it.name} url:$url", e)
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
        if (query.startsWith("http")) {
            return searchUrl(query)
        }
        return withIo {
            searchHistoryDao.insertSearchHistory(SearchHistory(query = query))
            //读取所有脚本。只读取一次，不接受后续更新
            val allJavaScript = getAllEnableBookSource()
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

    fun getAllSearchHistory(): Flow<List<SearchHistory>> {
        //查询全部搜索历史记录
        return searchHistoryDao.getAllSearchHistory()
    }

    fun deleteSearchHistory(list: List<SearchHistory>) {
        viewModelScope.launch(Dispatchers.IO) {
            searchHistoryDao.deleteSearchHistory(list)
        }
    }

    fun deleteAllSearchHistory() {
        //删除全部搜索历史记录
        viewModelScope.launch(Dispatchers.IO) {
            Log.i("删除全部搜索历史记录")
            searchHistoryDao.deleteAllSearchHistory()
        }

    }

}