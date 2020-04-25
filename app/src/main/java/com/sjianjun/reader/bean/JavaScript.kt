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


val defaultJavaScript by lazy {
    JavaScript(
        "默认JS对象", """
    function search(http,query){
        return null;
    }

    function getDetails(http,url){
        return null;
    }

    function getChapterContent(http,url){
        return null;
    }   
     //获取书城数据
    function getBookCityPageList(http,script){
        return null;
    }
""".trimIndent()
    )
}

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
    @JvmField
    var headerScript = """
        ${importClassCode<Jsoup>()}
        ${importClassCode<Log>()}
        ${importClassCode<SearchResult>()}
        ${importClassCode<Chapter>()}
        ${importClassCode<Book>()}
        ${importClassCode<StringUtil>()}
        ${importClassCode<Page>()}
        ${importClassCode<Page.BookGroup>()}

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
        return execute {
            val paramList = params.filter { it?.isNotEmpty() == true }
            val result = if (paramList.isEmpty()) {
                eval("${func.name}(http)")
            } else {
                val param = paramList.map { "\"$it\"" }.reduce { acc, s -> "$acc,$s" }
                eval("${func.name}(http,${param})")
            }
            jsToJava<T>(result)
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
            try {
                execute<List<SearchResult>>(Func.search, query)
            } catch (t: Throwable) {
                Log.i("$source 搜索出错：$query", t)
                null
            }
        }
    }

    suspend fun getDetails(bookUrl: String): Book? {
        return withIo {
            try {
                execute<Book>(Func.getDetails, bookUrl)
            } catch (t: Throwable) {
                Log.i("$source 加载出错 url：$bookUrl", t)
                null
            }
        }
    }

    suspend fun getChapterContent(chapterUrl: String): String? {
        return withIo {
            try {
                execute<String>(Func.getChapterContent, chapterUrl)
            } catch (t: Throwable) {
                Log.i("$source 加载章节内容出错：$chapterUrl", t)
                null
            }
        }
    }

    suspend fun getPageList(script: String): List<Page>? {
        return withIo {
            try {
                if (script.isEmpty()) {
                    execute<List<Page>>(Func.getPageList)
                } else {
                    execute {
                        jsToJava<List<Page>>(eval(script))
                    }
                }
            } catch (t: Throwable) {
                Log.i("$source 加载PageList出错：$script", t)
                null
            }
        }
    }

    suspend fun getBookList(script: String): List<Book>? {
        return withIo {
            try {
                if (script.isEmpty()) {
                    execute<List<Book>>(Func.getBookList)
                } else {
                    execute {
                        jsToJava<List<Book>>(eval(script))
                    }
                }
            } catch (t: Throwable) {
                Log.i("$source 加载BookList出错：$script", t)
                null
            }
        }
    }

    enum class Func {
        search, getDetails, getChapterContent, getPageList, getBookList
    }

}