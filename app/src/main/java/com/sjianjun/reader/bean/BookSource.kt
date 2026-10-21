package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.chaquo.python.Python
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.python.py
import com.sjianjun.reader.python.ScriptTaskRunner
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
import com.sjianjun.reader.utils.trimExt
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log

@Entity
class BookSource {

    @PrimaryKey
    var id: String = ""
        get() {
            if (field.isEmpty()) {
                field = "${group.trim()}:${name.trim()}"
            }
            return field
        }

    /**
     * 来源 与书籍[Book.source]对应。例如：笔趣阁
     */
    var name: String = ""

    /**
     * 分组名称
     */
    var group: String = ""

    /**
     * - js 脚本内容
     * - 多余的参数从arguments取
     */
    var js: String = ""

    /**
     * 阅读书源使用更新时间戳
     */
    var version: Long = -1
    var enable: Boolean = true
    var requestDelay: Long = 1000L

    /**
     * 书源校验结果
     */
    var checkResult: String? = null

    var checkErrorMsg: String? = null

    /**
     * 书源管理页面是否被选中
     */
    @Ignore
    var selected = false

    /**
     * 书源备注信息
     */
    var comment: String = ""

    /**
     * 脚本引擎语言
     */
    var lauanage: Language = Language.py

    private val yueduEngine by lazy {
        YueduEngineProxy(js)
    }


    private suspend inline fun <reified T> execute(
        func: Func,
        param: String?,
        param2: String? = null,
    ): T? {
//        Log.i("调用脚本方法：${func}")
        return when (lauanage) {
            Language.yuedu -> {
                val result = when (func) {
                    Func.search -> yueduEngine.search(param!!)
                    Func.getDetails -> yueduEngine.getDetails(param!!)
                    Func.getChapterContent -> yueduEngine.getChapterContent(param!!)
                    Func.getSiteUrl -> yueduEngine.getSiteUrl()
                    Func.verify -> yueduEngine.verify(param!!).toString()
                }
                if (T::class.java == String::class.java) {
                    return result as T?
                }
                if (T::class.java == Boolean::class.java) {
                    return result.toBoolean() as T
                }
                if (T::class.java == Int::class.java) {
                    return result?.toInt() as T?
                }

                try {
                    gson.fromJson<T>(result)
                } catch (e: Exception) {
                    Log.i("调用脚本方法：$func, 参数：$param, 返回结果：$result")
                    throw e
                }
            }

            Language.file -> throw UnsupportedOperationException("文件导入书源不支持执行脚本")
            Language.py -> py(func.name, param, param2)
        }
    }

    fun hasFunction(func: Func): Boolean {
        return when (lauanage) {
            Language.file -> false
            Language.yuedu -> true
            Language.py -> {
                val py = Python.getInstance()
                val exec = py.getModule("exec_script")
                exec.callAttr("has_function", js, func.name).toString().toBoolean()
            }
        }
    }

    /**
     * 校验成功返回true，校验失败返回false，不存在def verify()返回null
     */
    suspend fun verify(query: String): Boolean? {
        return withIo {
            try {
                if (!hasFunction(Func.verify)) {
                    return@withIo null
                }
//                返回0、1、2 分别代表：验证通过，验证失败，不支持验证
                val result = execute<Int?>(Func.verify, query)
                when (result) {
                    0 -> {
                        checkResult = "校验成功"
                        checkErrorMsg = null
                        return@withIo true
                    }

                    1 -> {
                        checkResult = "校验失败"
                        checkErrorMsg = "书源自行校验未通过"
                        return@withIo false
                    }

                    else -> {
                        checkResult = "不支持校验"
                        checkErrorMsg = "不支持校验"
                        return@withIo true
                    }
                }
            } catch (t: Throwable) {
                Log.e("$name 书源校验失败", t)
                checkResult = "校验失败：${t.message}"
                checkErrorMsg = t.message
                false
            }
        }
    }

    suspend fun getSiteUrl(): String? {
        return withIo {
            try {
                execute<String>(Func.getSiteUrl, null)
            } catch (t: Throwable) {
                Log.e("$name 书源网站地址获取失败", t)
                null
            }
        }
    }

    suspend fun isSupported(url: String): Boolean {
        return withIo {
            try {
                val site = getSiteUrl()
                val siteHost = site?.toHttpUrlOrNull()?.topPrivateDomain()
                val urlHost = url.toHttpUrlOrNull()?.topPrivateDomain()
                val result = siteHost != null && urlHost != null && siteHost == urlHost
                return@withIo result
            } catch (t: Throwable) {
                Log.e("$name 检查是否支持出错：$url", t)
                false
            }
        }
    }

    suspend fun search(query: String): List<SearchResult>? {
        return withIo {
            execute<List<SearchResult>>(Func.search, query)?.also { results ->
                // 聚合搜索源（如百度）可能返回**别的站点**的书：
                // 按 bookUrl 归属到真正支持它的书源，找不到再保留当前书源
                bindSourceByUrl(results)
            }
        }
    }

    /**
     * 把搜索结果归属到对应书源：
     * - URL 属于当前书源 → 当前书源；
     * - 否则在所有启用书源里找站点域名匹配的源（同一次搜索内缓存，避免重复跑 getSiteUrl）；
     * - 都找不到 → 保留当前书源（this@BookSource）。
     *
     * @param sources 候选书源，默认取数据库里所有启用书源（测试可显式传入）
     * @param hostOf 书源站点域名解析，默认跑书源的 getSiteUrl（测试可替换）
     */
    internal suspend fun bindSourceByUrl(
        results: List<SearchResult>,
        sources: List<BookSource>? = null,
        hostOf: (suspend (BookSource) -> String?)? = null,
    ) {
        if (results.isEmpty()) return
        val candidates = sources ?: enabledBookSources()
        val resolver: suspend (BookSource) -> String? = hostOf ?: { it.siteHost() }
        val hostCache = HashMap<String, String?>()
        // 同一次搜索内每个源只解析一次域名
        suspend fun host(source: BookSource): String? {
            if (hostCache.containsKey(source.id)) return hostCache[source.id]
            val value = resolver(source)
            hostCache[source.id] = value
            return value
        }
        results.forEach { result ->
            val owner = resolveSource(result.bookUrl, candidates) { host(it) } ?: this
            if (owner.id != id) {
                // 设备上可核对：getSiteUrl 不靠谱时也可能是这里改绑错了
                Log.i("$name 搜索结果归属书源：${result.bookTitle} ${result.bookUrl} -> ${owner.name}")
            }
            result.bookSource = owner
        }
    }

    private suspend fun enabledBookSources(): List<BookSource> = runCatching {
        com.sjianjun.reader.repository.DbFactory.db.bookSourceDao()
            .getAllBookSource()
            .filter { it.enable }
    }.getOrDefault(emptyList())

    /** 按 URL 的站点域名找书源；找不到返回 null（调用方回退到当前书源） */
    private suspend fun resolveSource(
        url: String,
        sources: List<BookSource>,
        host: suspend (BookSource) -> String?,
    ): BookSource? {
        val urlHost = url.toHttpUrlOrNull()?.topPrivateDomain() ?: return null
        // 先判断是不是本站：绝大多数搜索结果走这条，不需要扫其他源
        if (host(this) == urlHost) return this
        return sources.firstOrNull { it.id != id && host(it) == urlHost }
    }

    /**
     * 书源站点域名（跑一次 getSiteUrl）。
     *
     * 加 3 秒超时：个别书源的 getSiteUrl 会发网络请求，卡住时不能把整个搜索结果显示拖死；
     * 超时当作"拿不到域名"，按兜底逻辑保留搜索源。
     */
    private suspend fun siteHost(): String? = runCatching {
        kotlinx.coroutines.withTimeoutOrNull(3_000) { getSiteUrl() }
    }
        .getOrNull()
        ?.toHttpUrlOrNull()
        ?.topPrivateDomain()

    /**
     * 获取书籍详情（含章节列表）
     * @param bookUrl 书籍详情链接
     * @param lastChapterUrl 本地已有的最新章节链接，没有时传空串。
     *        脚本可据此判断当前是加载全部章节还是只加载最新章节，用于优化检查更新时的速度。
     *        注意：章节列表必须升序返回；增量返回时请包含该章节，应用以返回的第一个章节在本地定位，
     *        定位位置及其之后的本地章节以返回数据为准（可多返回本地已有的章节，应用会用返回数据覆盖）。
     */
    suspend fun getDetails(bookUrl: String, lastChapterUrl: String = ""): Book? {
        return withIo {
            val book = execute<Book>(Func.getDetails, bookUrl, lastChapterUrl)
            book?.bookSourceId = id
            //脚本可能不返回书籍链接，此时用请求链接兜底，避免书籍id为空导致章节删除、数据更新错位
            if (book != null && book.url.isBlank()) {
                book.url = bookUrl
            }
            if (book?.title.isNullOrBlank()) {
                Log.e("$name 获取书籍详情失败：$bookUrl")
                throw IllegalStateException("获取书籍详情失败")
            }
            book
        }
    }

    suspend fun getChapterContent(chapterUrl: String): ChapterContent {
        return withIo {
            try {
                val text = execute<String>(Func.getChapterContent, chapterUrl) ?: ""
                val content = if (text.startsWith("{") && text.endsWith("}")) {
                    gson.fromJson<ChapterContent>(text)!!
                } else {
                    ChapterContent().apply {
                        this.content = text
                    }
                }
                content.content = content.content?.trimExt()
                if (content.content.isNullOrBlank()) {
                    content.content = "章节内容加载失败"
                    content.contentError = true
                }
                content
            } catch (t: Throwable) {
                //脚本被取消（页面退出、被更新的请求取代）：不能当成"内容加载失败"写进缓存，
                //否则阅读页会显示"章节内容加载失败: ScriptAborted"，且这一章会被标记成失败
                if (ScriptTaskRunner.isCancelled(t)) {
                    Log.i("$name 章节内容加载被取消：$chapterUrl")
                    throw t
                }
                Log.e("$name 加载章节内容出错：$chapterUrl", t)
                ChapterContent().apply {
                    this.content = "章节内容加载失败: ${t.message}"
                    this.contentError = true
                }
            }
        }
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BookSource

        if (id != other.id) return false
        if (name != other.name) return false
        if (group != other.group) return false
        if (js != other.js) return false
        if (version != other.version) return false
        if (requestDelay != other.requestDelay) return false
        if (comment != other.comment) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + group.hashCode()
        result = 31 * result + js.hashCode()
        result = 31 * result + requestDelay.hashCode()
        result = 31 * result + (comment.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "BookSource(id='$id', name='$name', group='$group', js='$js', version=$version, enable=$enable, requestDelay=$requestDelay, checkResult=$checkResult, checkErrorMsg=$checkErrorMsg, comment=$comment, lauanage=$lauanage)"
    }


    enum class Func {
        search, getDetails, getChapterContent, getSiteUrl, verify
    }

    enum class Language {
        /**
         * 阅读书源
         */
        yuedu,

        /**
         * Python书源
         */
        py,

        /**
         * 文件导入书源，什么都不做，仅作为标识使用
         */
        file
    }

    companion object {
        val fileImportBookSource = BookSource().apply {
            id = "文件导入"
            name = "文件导入"
            group = "本地"
            js = ""
            version = 1L
            enable = true
            requestDelay = 0L
            comment = "文件导入书源，什么都不做，仅作为标识使用"
            lauanage = Language.file
        }
    }

}
