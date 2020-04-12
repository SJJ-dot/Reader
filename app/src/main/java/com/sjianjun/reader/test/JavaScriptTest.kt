package com.sjianjun.reader.test

import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.ChapterContent
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.bean.JavaScript.Func.*
import com.sjianjun.reader.bean.SearchResult
import kotlinx.coroutines.runBlocking
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
        parse.select("atc > img")
        parse.getElementsByTag("book")
        parse.child(0).ownText()
        parse.attr("content").replace("format=html5; url=","")
        parse.getElementsByAttributeValue("","")
        val children = parse.children()
        "".replace("m.","")
    }

    val javaScript = JavaScript("古古小说网（55小说网）") {
        """
            var source = "古古小说网（55小说网）";
            var baseUrl = "http://www.55shuba.com";
            function search(http,query){
                var map = new HashMap();
                map.put("searchkey",URLEncoder.encode(query,"gbk"))
                map.put("searchtype","articlename")
                map.put("submit","%CB%D1%CB%F7")
                var html = http.post(baseUrl+"/modules/article/search.php",map);
                var parse = Jsoup.parse(html,baseUrl);
                
                try{
                    var bookList = parse.getElementsByClass("listtab").get(0).children();
                    var results = new ArrayList();
                    for (var i=0;i<bookList.size();i++){ 
                        var bookElement = bookList.get(i);
                        var result = new SearchResult();
                        result.source = source;
                        result.bookTitle = bookElement.getElementsByClass("name").get(0).getElementsByTag("a").text();
                        result.bookUrl = bookElement.getElementsByClass("name").get(0).getElementsByTag("a").get(0).absUrl("href");
                        result.bookAuthor = bookElement.getElementsByClass("author").text();
                        result.bookCover = bookElement.getElementsByClass("c").get(0).getElementsByTag("img").get(0).absUrl("src");
                        result.latestChapter = bookElement.getElementsByClass("chapter").get(0).getElementsByTag("a").text();
                        results.add(result);
                    }

                    return results;
                }catch(error){
                    Log.e("小说列表解析失败，尝试解析单个小说"+error.message);
                    var bookInfo = parse.getElementById("book");
                    var results = new ArrayList();
                    var result = new SearchResult();
                    result.source = source;
                    result.bookTitle = bookInfo.child(0).ownText();
                    result.bookUrl = parse.getElementsByAttributeValue("http-equiv","mobile-agent").attr("content").replace("format=html5; url=http://m","http://www");
                    result.bookAuthor = bookInfo.child(0).getElementsByTag("a").text();
                    result.bookCover = bookInfo.getElementsByClass("atc").get(0).getElementsByTag("img").get(0).absUrl("src");
                    result.latestChapter = bookInfo.getElementsByClass("last").get(0).getElementsByTag("a").text();
                    results.add(result);
                    
                    return results;
                }
            }

            /**
             * 书籍来源[JavaScript.source]
             */
            function getDetails(http,url){
                var parse = Jsoup.parse(http.get(url),url);
                var book = new Book();
                book.source = source;
                //书籍信息
                var bookInfo = parse.getElementById("book");
                book.url = url;
                Log.e("title");
                book.title = bookInfo.child(0).ownText();
                Log.e("author");
                book.author = bookInfo.child(0).getElementsByTag("a").text();
                Log.e("intro");
                book.intro = bookInfo.getElementsByClass("intro").html();
                Log.e("cover");
                book.cover = bookInfo.getElementsByClass("atc").get(0).getElementsByTag("img").get(0).absUrl("src");
                Log.e("chapterListUrl");
                //加载章节列表
                var chapterListUrl = bookInfo.getElementsByClass("btn cl").get(0).child(0).child(0).absUrl("href");
                Log.e("chapterListHtml url:"+chapterListUrl);
                var chapterListHtml = Jsoup.parse(http.get(chapterListUrl),chapterListUrl);
                
                var children = chapterListHtml.getElementsByClass("list").select("a");
                Log.e("children ");
                var chapterList = new ArrayList();
                for(i=0; i<children.size(); i++){
                    var chapterEl = children.get(i);
                    var chapter = new Chapter();
                    chapter.title = chapterEl.text();
                    chapter.url = chapterEl.absUrl("href");
                    chapterList.add(chapter);
                }
                book.chapterList = chapterList;
                Log.e(book)
                return book;
            }
            
            function getChapterContent(http,url){
                var parse = Jsoup.parse(http.get(url),url);
                var content = parse.getElementById("contents").html();
                return content;
            }
            
        """.trimIndent()
    }

    fun testJavaScript(): Unit = runBlocking {
//        val query = "黎明之剑"
        val query = "哈利波特"
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
            chapter.content = ChapterContent(chapter.url, chapter.bookUrl, c ?: "")
            Log.e("${javaScript.source} 章节加载结果:$chapter")
        }
        Unit
    }
}