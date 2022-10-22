package com.sjianjun.reader.bean

import androidx.room.Ignore
import com.google.gson.annotations.Expose
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.http.CookieMgr
import com.sjianjun.reader.http.http
import com.sjianjun.reader.rhino.ContextWrap
import com.sjianjun.reader.rhino.importClassCode
import com.sjianjun.reader.rhino.js
import com.sjianjun.reader.utils.md5
import okhttp3.HttpUrl
import org.jsoup.Jsoup
import org.jsoup.internal.StringUtil
import sjj.alog.Log


data class BookSource constructor(
    /**
     * 来源 与书籍[Book.source]对应。例如：笔趣阁
     */
    @JvmField
    var source: String,

    /**
     * - js 脚本内容
     * - 多余的参数从arguments取
     */
    @JvmField
    var js: String,
    var version: Int,
    var isOriginal: Boolean = false,
    @JvmField
    var enable: Boolean = true,
    var requestDelay: Long = 1000L,
    var website: String = ""
) {

    val jsProps = mutableListOf<Pair<String, Any>>()

    /**
     * 书源管理页面是否被选中
     */
    @Expose(serialize = false)
    var selected = false

    /**
     * 书源校验结果
     */
    @Expose(serialize = false)
    var checkResult: String? = null

    @Expose(serialize = false)
    var checkErrorMsg: String? = null


    inline fun <reified T> execute(func: Func, vararg params: String?): T? {
        return execute(func.name, *params)
    }

    inline fun <reified T> execute(functionName: String, vararg params: String?): T? {
        return execute {
            val paramList = params.filter { it?.isNotEmpty() == true }
            val result = if (paramList.isEmpty()) {
                eval("${functionName}()")
            } else {
                val param = paramList.map { "\"$it\"" }.reduce { acc, s -> "$acc,$s" }
                eval("${functionName}(${param})")
            }
            jsToJava<T>(result)
        }
    }


    inline fun <reified T> execute(runner: ContextWrap.() -> T?): T? {
        return js {
            putProperty("source", javaToJS(source))
            putProperty("http", javaToJS(http))
            jsProps.forEach {
                putProperty(it.first, javaToJS(it.second))
            }
//            putProperty("context", this)

            eval(headerScript)
            eval(js)
            runner()
        }
    }

    suspend fun search(query: String): List<SearchResult>? {
        return withIo {
            execute<List<SearchResult>>(Func.search, query)?.also {
                it.forEach {
                    it.source = source
                }
            }
        }
    }

    suspend fun getDetails(bookUrl: String): Book? {
        return withIo {
            execute<Book>(Func.getDetails, bookUrl)?.also {
                it.source = source
            }
        }
    }

    suspend fun getChapterContent(chapterUrl: String): String? {
        return withIo {
            try {
                execute<String>(Func.getChapterContent, chapterUrl)
            } catch (t: Throwable) {
                Log.e("$source 加载章节内容出错：$chapterUrl", t)
                null
            }
        }
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BookSource

        if (source != other.source) return false

        return true
    }

    override fun hashCode(): Int {
        return source.hashCode()
    }


    enum class Func {
        search, getDetails, getChapterContent
    }

}

val headerScript = """
        ${importClassCode<Jsoup>()}
        ${importClassCode<Log>()}
        ${importClassCode<CookieMgr>()}
        ${importClassCode<SearchResult>()}
        ${importClassCode<Chapter>()}
        ${importClassCode<Book>()}
        ${importClassCode<StringUtil>()}
        ${importClassCode<HttpUrl>()}

        importClass(Packages.java.util.ArrayList)
        importClass(Packages.java.util.HashMap)
        importClass(Packages.java.net.URLEncoder)
        importClass(Packages.java.net.URLDecoder)
        
        function request(params){
            // url type header data enc
            var header = new HashMap();
            var hd = params.header || {}
            for(k in hd){
                header.put(k,hd[k])
            }
            
            var query = new HashMap();
            var data = params.data || {}
            var enc = params.enc || "utf-8"
            for(k in data){
                query.put(k,URLEncoder.encode(data[k],enc))
            }
            
            var url;
            if(params.baseUrl==undefined){
                url = HttpUrl.get(params.url).url().toString()
            }else{
                url = HttpUrl.get(params.baseUrl).newBuilder(params.url).build().url().toString()
            }
            
            var resp;
            if(params.type=="post"){
                resp = http.post(url,query,header)
            }else{
                resp = http.get(url,query,header)
            }
            return Jsoup.parse(resp,url);
        }
        
        function get(params){
            params.type = "get"
            return request(params)
        }
        
        function post(params){
            params.type = "post"
            return request(params)
        }
        
        function encode(s,enc){
            return URLEncoder.encode(s,enc||"utf-8")
        }
        
        function decode(s,enc){
            return URLEncoder.decode(s,enc||"utf-8")
        }
        
        function getCookie(url,name){
            return CookieMgr.getCookie(url,name)
        }
        
    """.trimIndent()