package com.sjianjun.reader.test

import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.bean.JavaScript.Func.doSearch
import com.sjianjun.reader.bean.SearchResult
import kotlinx.coroutines.runBlocking
import sjj.alog.Log
import sjj.novel.util.fromJson
import sjj.novel.util.gson

class JavaScriptTest {
    val javaScript = JavaScript("biquge5200") {
        """
            
            var baseUrl = "https://www.biquge5200.cc"
            function doSearch(http,query){
                var html = http.get(baseUrl+"/modules/article/search.php?searchkey="+encodeURIComponent(query))
                var parse = Jsoup.parse(html,);
                var trs = parse.getElementById("hotcontent").getElementsByTag("tbody").get(0).getElementsByTag("tr");
                var results = new ArrayList();
                for (var i=1;i<trs.size();i++){ 
                    var tr = trs.get(i);
                    var result = new SearchResult();
                    result.setBookTitle(tr.child(0).child(0).text());
                    result.setBookUrl(tr.child(0).child(0).absUrl("href"));
                    result.setBookAuthor(tr.child(2).text());
                    //没有封面 result['bookCover']
                    var chapter = new Chapter();
                    chapter.setTitle(tr.child(1).child(0).text());
                    chapter.setUrl(tr.child(1).child(0).absUrl("href"));
                    result.setLastChapter(chapter);
                    results.add(result);
                }

                return results;
            }
        """.trimIndent()
    }

    fun testJavaScriptSearch(): Unit = runBlocking {
        val result = javaScript.execute<List<SearchResult>>(doSearch, "黎明之剑")
        Log.e(result)
//        Log.e(gson.fromJson<List<SearchResult>>(result ?: ""))
    }
}