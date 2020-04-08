package com.sjianjun.reader.test

import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.bean.JavaScript.Func.*
import com.sjianjun.reader.bean.SearchResult
import kotlinx.coroutines.runBlocking
import sjj.alog.Log

object JavaScriptTest {
    val javaScript = JavaScript("biquge5200") {
        """
            var source = "biquge5200";
            var baseUrl = "https://www.biquge5200.cc";
            function search(http,query){
                var html = http.get(baseUrl+"/modules/article/search.php?searchkey="+encodeURIComponent(query));
                var parse = Jsoup.parse(html,baseUrl);
                var trs = parse.getElementById("hotcontent").getElementsByTag("tbody").get(0).getElementsByTag("tr");
                var results = new ArrayList();
                for (var i=1;i<trs.size();i++){ 
                    var tr = trs.get(i);
                    var result = new SearchResult();
                    result.source = source;
                    result.bookTitle = tr.child(0).child(0).text();
                    result.bookUrl = tr.child(0).child(0).absUrl("href");
                    result.bookAuthor = tr.child(2).text();
                    //没有封面 result.bookCover
                    result.latestChapter = tr.child(1).child(0).text();
                    results.add(result);
                }

                return results;
            }

            /**
             * 书籍来源[JavaScript.source]
             */
            function getDetails(http,url){
                var parse = Jsoup.parse(http.get(url),baseUrl);
                var book = new Book();
                book.source = source;
                //书籍信息
                var info = parse.getElementById("info");
                book.url = url;
                book.title = info.child(0).text();
                book.author = info.child(1).text().split("：")[1];
                book.intro = parse.getElementById("intro").text();
                book.cover = parse.getElementById("fmimg").child(0).absUrl("src");
                //加载章节列表
                var chapterList = new ArrayList();
                var children = parse.getElementById("list").child(0).children();
                for(i=children.size()-1; i>0; i--){
                    var e = children.get(i);
                    if(e.tagName() == "dt"){
                        break;
                    }
                    var chapter = new Chapter();
                    chapter.title = e.child(0).text();
                    chapter.url = e.child(0).absUrl("href");
                    chapterList.add(0,chapter);
                }
                book.chapterList = chapterList;
                return book;
            }
            
            function getChapterContent(http,url){
                var parse = Jsoup.parse(http.get(url),baseUrl);
                var content = parse.getElementById("content").html();
                return content;
            }
            
        """.trimIndent()
    }

    fun testJavaScript(): Unit = runBlocking {
        val query = "黎明之剑"
        Log.e("${javaScript.source} 搜索 $query")
        val result = javaScript.execute<List<SearchResult>>(search, query)
        if (result.isNullOrEmpty()) {
            Log.e("${javaScript.source} 搜索结果为空")
            return@runBlocking
        }
        Log.e("${javaScript.source} 加载书籍详情 ${result.first().bookTitle}")
        val book = javaScript.execute<Book>(getDetails, result.first().bookUrl)
        if (book == null) {
            Log.e("${javaScript.source}  书籍加载失败")
            return@runBlocking
        }
        Log.e("${javaScript.source} 加载章节内容:${book.chapterList?.firstOrNull()?.title}")
        val chapter = book.chapterList?.firstOrNull()
        if (chapter != null) {
            val c = javaScript.execute<String>(getChapterContent, chapter.url)
            chapter.content = c ?: ""
            Log.e("${javaScript.source} 章节加载结果:$chapter")
        }
        Unit
    }
}