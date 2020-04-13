package com.sjianjun.reader.test

import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.ChapterContent
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.bean.JavaScript.Func.*
import com.sjianjun.reader.bean.SearchResult
import com.sjianjun.reader.utils.withIo
import org.jsoup.Jsoup
import sjj.alog.Log

/**
 * 已解析：biquge5200.cc、古古小说网（55小说网）
 * 不支持：乐安宣书网(搜索结果没有作者)、
 */
object JavaScriptTest {
    fun showName() {
        val parse = Jsoup.parse("")
        parse.getElementsByTag("")
        parse.getElementById("")
        parse.getElementsByClass("").get(0).html()
        parse.select("atc > img").select("")
        parse.getElementsByTag("book")
        parse.child(0).ownText()
        parse.attr("content").replace("format=html5; url=", "")
        parse.getElementsByAttributeValue("", "")
        val children = parse.children()
        "".replace("m.", "")
    }

    val javaScript = JavaScript("爱上中文6") {
        """
            function search(http,query){
                var baseUrl = "https://www.aszw6.com/";
                var map = new HashMap();
                map.put("searchkey",URLEncoder.encode(query,"gbk"))
                map.put("searchtype","articlename")
                var html = http.post(baseUrl+"/modules/article/search.php",map);
                var parse = Jsoup.parse(html,baseUrl);
                
           
                var bookList = parse.getElementById("centerm").select("tbody").select("tr");
                var results = new ArrayList();
                for (var i=1;i<bookList.size();i++){ 
                    var bookElement = bookList.get(i);
                    var result = new SearchResult();
                    result.source = source;
                    result.bookTitle = bookElement.select("a").get(0).text();
                    result.bookUrl = bookElement.select("a").get(0).absUrl("href");
                    result.bookAuthor = bookElement.select("a").get(2).text();
                    //result.bookCover = bookElement.getElementsByClass("c").get(0).getElementsByTag("img").get(0).absUrl("src");
                    result.latestChapter = bookElement.select("a").get(1).text();
                    results.add(result);
                }
                return results;
            }

            /**
             * 书籍详情[JavaScript.source]
             */
            function getDetails(http,url){
                var parse = Jsoup.parse(http.get(url),url);
                var book = new Book();
                book.source = source;
                //书籍信息
                var bookInfo = parse.select(".info");
                book.url = url;
                book.title = bookInfo.select(".btitle").get(0).child(0).text();
                book.author = bookInfo.select(".btitle").get(0).child(1).text();
                book.intro = bookInfo.select(".js").get(0).html();
                book.cover = bookInfo.select(".pic > img").get(0).absUrl("src");
                //加载章节列表
//                var chapterListUrl = bookInfo.getElementsByClass("btn cl").get(0).child(0).child(0).absUrl("href");
//                var chapterListHtml = Jsoup.parse(http.get(chapterListUrl),chapterListUrl);
                
                var children = parse.select("#at a");
                Log.e(children);
                var chapterList = new ArrayList();
                for(i=0; i<children.size(); i++){
                    var chapterEl = children.get(i);
                    var chapter = new Chapter();
                    chapter.title = chapterEl.text();
                    chapter.url = chapterEl.absUrl("href");
                    chapterList.add(chapter);
                }
                book.chapterList = chapterList;
                return book;
            }
            
            function getChapterContent(http,url){
                var parse = Jsoup.parse(http.get(url),url);
                var content = parse.getElementById("contents").html();
                return content;
            }
            
        """.trimIndent()
    }

    suspend fun testJavaScript(): Unit = withIo {
        //        val query = "黎明之剑"
        val query = "哈利波特"
        Log.e("${javaScript.source} 搜索 $query")
        val result = javaScript.execute<List<SearchResult>>(search, query)
        if (result.isNullOrEmpty()) {
            Log.e("${javaScript.source} 搜索结果为空")
            return@withIo
        }
        val first = result.first()
        Log.e("${first.source} 加载书籍详情 ${first.bookTitle} ${first.bookUrl}")
        val book = javaScript.execute<Book>(getDetails, first.bookUrl)
        if (book == null) {
            Log.e("${first.source}  书籍加载失败")
            return@withIo
        }
        Log.e("${book.source} 加载章节内容:${book.chapterList?.firstOrNull()?.title}")
        val chapter = book.chapterList?.firstOrNull()
        if (chapter != null) {
            val c = javaScript.execute<String>(getChapterContent, chapter.url)
            chapter.content = ChapterContent(chapter.url, chapter.bookUrl, c ?: "")
            Log.e(" 测试：${if (c.isNullOrBlank()) "失败" else "通过"}")
        }
        Unit
    }
}