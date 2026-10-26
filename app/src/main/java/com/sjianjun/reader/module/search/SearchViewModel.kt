package com.sjianjun.reader.module.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.sjianjun.reader.utils.toBookList
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log

class SearchViewModel : ViewModel() {
    /** 单次会话内只提示一次"阅读书源已不支持"，避免并发搜索时重复 toast */
    private var yueduUnsupportedTipShown = false
    private val bookSourceDao get() = DbFactory.db.bookSourceDao()
    private val searchHistoryDao get() = DbFactory.db.searchHistoryDao()
    private val bookDao get() = DbFactory.db.bookDao()
    private val readingRecordDao get() = DbFactory.db.readingRecordDao()

    fun getAllEnableBookSource(): List<BookSource> {
        return bookSourceDao.getAllBookSource().filter { it.enable }
    }

    suspend fun saveSearchResult(searchResult: List<SearchResult>): String = withIo {
        val incoming = searchResult.filter { !it.bookSource?.id.isNullOrBlank() }.toBookList()
        if (incoming.isEmpty()) return@withIo ""
        // 同一书源内只看 URL **相对站点域名的路径**是否重复：
        // http/https、www/m 等镜像域名不同但路径相同，视为同一本书；
        // 路径不同则是两本书（即使书名/作者相同）。
        // 优先复用数据库已有的（章节/阅读进度都挂在它的 id 上）。
        val sourceBooks = HashMap<String, List<Book>>()
        val byKey = LinkedHashMap<String, Book>()
        incoming.forEach { book ->
            val key = "${book.bookSourceId}|${relativeBookPath(book.url)}"
            val existing = byKey[key] ?: run {
                val books = sourceBooks.getOrPut(book.bookSourceId) {
                    bookDao.getBooksBySourceId(book.bookSourceId)
                }
                val path = relativeBookPath(book.url)
                books.firstOrNull { relativeBookPath(it.url) == path }
            }
            byKey[key] = existing ?: book
        }
        val bookList = byKey.values.toList()
        DbFactory.db.runInTransaction<String> {
            BookUseCase.cleanDirtyData()
            bookDao.insertBook(bookList)
            val book = bookList.first()
            val readingRecord = readingRecordDao.getReadingRecordSync(book.title)
            Log.i("保存搜索结果记录：$readingRecord $bookList")
            val readingBook = readingRecord?.let { bookDao.getBookById(it.bookId) }
            if (readingBook == null) {
                readingRecordDao.insertReadingRecord(ReadingRecord(book.title, book.id))
                book.id
            } else {
                readingBook.id
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
                    if (it.lauanage == BookSource.Language.yuedu && !yueduUnsupportedTipShown) {
                        yueduUnsupportedTipShown = true
                        toast(BookSource.YUEDU_UNSUPPORTED)
                    }
                    Log.e("获取书籍详情失败 ${it.name} url:$url", e)
                    null
                }
            }
        }
        val results = books.awaitAll().filterNotNull()
        val group = mutableMapOf<String, MutableList<SearchResult>>()
        val seen = HashSet<String>()
        results.forEach { result -> addSearchResult(group, seen, result) }
        return@withIo flowOf(group.values.toList())
    }

    /**
     * 把搜索结果加入分组（同一本书去重）。
     *
     * 同一书源内按 URL **相对站点域名的路径**判定：镜像域名/协议不同但路径相同视为同一本
     * （通用搜索源和书源自身可能重复返回）；不同书源的同路径书籍保留，换源时要用。
     */
    internal fun addSearchResult(
        group: MutableMap<String, MutableList<SearchResult>>,
        seen: MutableSet<String>,
        result: SearchResult,
    ) {
        // 同一书源内只看相对站点域名的路径（镜像域名/协议不同但路径相同 = 同一本）
        val sourceId = result.bookSource?.id.orEmpty()
        if (!seen.add("${result.key}|${relativeBookPath(result.bookUrl)}|$sourceId")) return
        group.getOrPut(result.key) { mutableListOf() }.add(result)
    }

    /**
     * URL 相对站点域名的地址：忽略 http/https 和域名/子域名差异，只取路径（去掉尾部斜杠）。
     *
     * 例如 `http://a.com/book/1` 与 `https://m.a.com/book/1` → `/book/1`，同一书源下视为同一本；
     * `/book/1` 与 `/bookquery/abc` 是不同地址，按两本处理。
     */
    internal fun relativeBookPath(url: String): String {
        val trimmed = url.trim()
        val path = trimmed.toHttpUrlOrNull()?.encodedPath ?: trimmed.substringAfter("://", trimmed)
        return path.trimEnd('/')
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
            // 去重集合：通用搜索源和书源自身可能返回同一本书
            val seen = HashSet<String>()
            allJavaScript.asFlow().flatMapMerge {
                //读取每一个发射项目，搜索。创建异步流，并发收集数据
                flow {
                    try {
                        Log.i("search ${it.id} start")
                        //同一书源只保留最后一次搜索（py 内部按书源生成标识，不区分关键词）
                        val search = it.search(query)
                        if (search != null) {
                            emit(search)
                            Log.i("search ${it.id} resultNum:${search.size}")
                        }
                    } catch (e: Exception) {
                        if (it.lauanage == BookSource.Language.yuedu && !yueduUnsupportedTipShown) {
                            yueduUnsupportedTipShown = true
                            toast(BookSource.YUEDU_UNSUPPORTED)
                        }
                        Log.e("search error ${it.id}", e)
                    }
                }
            }.map {
                //数据分组返回
                it.forEach { result -> addSearchResult(group, seen, result) }
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