package com.sjianjun.reader.rhino

import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.SearchResult
import com.sjianjun.reader.http.CookieMgr
import com.sjianjun.reader.http.http
import com.sjianjun.reader.utils.AesUtil
import okhttp3.HttpUrl
import org.jsoup.Jsoup
import org.jsoup.internal.StringUtil
import sjj.alog.Log



inline fun <reified T> BookSource.execute(functionName: String, vararg params: String?): T? {
    return execute {
        val paramList = params.filter { it?.isNotEmpty() == true }
        val result = if (paramList.isEmpty()) {
            eval("${functionName}()","call_func0")
        } else {
            val param = paramList.map { "\"$it\"" }.reduce { acc, s -> "$acc,$s" }
            eval("${functionName}(${param})","call_func0")
        }
        jsToJava<T>(result)
    }
}


inline fun <reified T> BookSource.execute(runner: ContextWrap.() -> T?): T? {
    return js {
        //以前的脚本使用了这个值。以后应该删除这个属性
        putProperty("source", javaToJS(name))
        putProperty("http", javaToJS(http))
        jsProps.forEach {
            putProperty(it.first, javaToJS(it.second))
        }
//            putProperty("context", this)

        eval(headerScript,"headerScript")
        eval(js,"BookSource_${name}")
        runner()
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
        ${importClassCode<AesUtil>()}

        importClass(Packages.java.util.ArrayList)
        importClass(Packages.java.util.HashMap)
        importClass(Packages.java.net.URLEncoder)
        importClass(Packages.java.net.URLDecoder)
        var requestUrl;
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
    
            if(params.baseUrl==undefined){
                requestUrl = HttpUrl.get(params.url).url().toString()
            }else{
                requestUrl = HttpUrl.get(params.baseUrl).newBuilder(params.url).build().url().toString()
            }
            
            var resp;
            if(params.type=="post"){
                resp = http.post(requestUrl,query,header)
            }else{
                resp = http.get(requestUrl,query,header)
            }
            return resp;
        }
        
        function get(params){
            params.type = "get"
            return Jsoup.parse(request(params).body,requestUrl)
        }
        
        function post(params){
            params.type = "post"
            return Jsoup.parse(request(params).body,requestUrl)
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