package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.sjianjun.reader.http.http
import com.sjianjun.reader.rhino.ContextWrap
import com.sjianjun.reader.rhino.importClassCode
import com.sjianjun.reader.rhino.js
import com.sjianjun.reader.utils.withIo
import org.jsoup.Jsoup
import org.jsoup.internal.StringUtil
import sjj.alog.Log
import java.util.concurrent.ConcurrentHashMap


@Entity
data class JavaScript constructor(
    /**
     * 来源 与书籍[Book.source]对应。例如：笔趣阁
     */
    @PrimaryKey
    @JvmField
    var source: String = "",

    /**
     * - js 脚本内容
     * - 多余的参数从arguments取
     */
    @JvmField
    var js: String = "",

    var version: Int = 0,

    var isStartingStation: Boolean = false,
    var priority: Int = 0,
    var supportBookCity: Boolean = false
) {

    @JvmField
    var enable = true

    @Ignore
    val FIELD_NULL_VALUE = Any()

    @Ignore
    val fieldsMap = ConcurrentHashMap<String, Any?>()

    inline fun <reified T : Any> getScriptField(fieldName: String): T? {
        if (fieldsMap.contains(fieldName)) {
            val value = fieldsMap[fieldName]
            return if (value === FIELD_NULL_VALUE) {
                null
            } else {
                value as T
            }
        }
        return try {
            val value = execute<T>("$fieldName;") ?: FIELD_NULL_VALUE
            fieldsMap[fieldName] = value
            value as T?
        } catch (throwable: Throwable) {
            fieldsMap[fieldName] = FIELD_NULL_VALUE
            null
        }
    }



    @Ignore
    @JvmField
    var headerScript = """
        ${importClassCode<Jsoup>()}
        ${importClassCode<Log>()}
        ${importClassCode<SearchResult>()}
        ${importClassCode<Chapter>()}
        ${importClassCode<Book>()}
        ${importClassCode<StringUtil>()}

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
            
            if(params.type=="post"){
                return http.post(params.url,query,header)
            }else{
                return http.get(params.url,query,header)
            }
        }
        
        function get(params){
            params.type = "get"
            return request(params)
        }
        
        function post(params){
            params.type = "post"
            return request(params)
        }
        
    """.trimIndent()

    inline fun <reified T> execute(func: Func, vararg params: String?): T? {
        return execute(func.name, *params)
    }

    inline fun <reified T> execute(functionName: String, vararg params: String?): T? {
        return execute {
            val paramList = params.filter { it?.isNotEmpty() == true }
            val result = if (paramList.isEmpty()) {
                eval("${functionName}(http)")
            } else {
                val param = paramList.map { "\"$it\"" }.reduce { acc, s -> "$acc,$s" }
                eval("${functionName}(http,${param})")
            }
            jsToJava<T>(result)
        }
    }


    inline fun <reified T> execute(script: String): T? {
        return execute {
            jsToJava<T>(eval(script))
        }
    }

    inline fun <reified T> execute(runner: ContextWrap.() -> T?): T? {
        return js {
            putProperty("source", javaToJS(source))
            putProperty("http", javaToJS(http))
            putProperty("context", this)

            eval(headerScript)
            eval(js)
            runner()
        }
    }

    suspend fun search(query: String): List<SearchResult>? {
        return withIo {
            execute<List<SearchResult>>(Func.search, query)
        }
    }

    suspend fun getDetails(bookUrl: String): Book? {
        return withIo {
            execute<Book>(Func.getDetails, bookUrl)
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


    suspend fun loadBookList(script: String): List<Book>? {
        return withIo {
            if (script.isEmpty()) {
                execute<List<Book>>(Func.loadBookList)
            } else {
                execute {
                    jsToJava<List<Book>>(eval(script))
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JavaScript

        if (source != other.source) return false
        if (js != other.js) return false
        if (version != other.version) return false
        if (isStartingStation != other.isStartingStation) return false
        if (priority != other.priority) return false
        if (supportBookCity != other.supportBookCity) return false
        if (enable != other.enable) return false
        if (fieldsMap != other.fieldsMap) return false

        return true
    }

    override fun hashCode(): Int {
        var result = source.hashCode()
        result = 31 * result + js.hashCode()
        result = 31 * result + version
        result = 31 * result + isStartingStation.hashCode()
        result = 31 * result + priority
        result = 31 * result + supportBookCity.hashCode()
        result = 31 * result + enable.hashCode()
        result = 31 * result + fieldsMap.hashCode()
        return result
    }

    enum class Func {
        search, getDetails, getChapterContent, loadPage, loadBookList
    }

}