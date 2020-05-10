package com.sjianjun.reader.test

import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.ChapterContent
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.bean.JavaScript.Func.*
import com.sjianjun.reader.bean.SearchResult
import com.sjianjun.reader.utils.withIo
import org.jsoup.Jsoup
import org.jsoup.internal.StringUtil
import sjj.alog.Log

/**
 * 已解析：biquge5200.cc、古古小说网（55小说网）
 * 不支持：乐安宣书网(搜索结果没有作者)、
 */
object JavaScriptTest {
    var test = false

    val javaScript = JavaScript(
        "文学迷",
        """
var baseUrl = "https://www.wenxuemi6.com/";
function search(http,query){
    
    var html = http.get(baseUrl + "search.php?q=" + URLEncoder.encode(query, "utf-8"));
    var parse = Jsoup.parse(html, baseUrl);
    var bookListEl = parse.select(".result-list > *");
    var results = new ArrayList();
    for (var i=0;i<bookListEl.size();i++){
        var bookEl = bookListEl.get(i);
        var result = new SearchResult();
        result.source = source;
        result.bookTitle = bookEl.select("div > div.result-game-item-detail > h3 > a").text();
        result.bookUrl = bookEl.select("div > div.result-game-item-detail > h3 > a").get(0).absUrl("href");
        result.bookAuthor = bookEl.select("div > div.result-game-item-detail > div > p:nth-child(1) > span:nth-child(2)").text();
        result.bookCover = bookEl.select("div > div.result-game-item-pic > a > img").get(0).absUrl("src");
        result.latestChapter = bookEl.select("div > div.result-game-item-detail > div > p:nth-child(4) > a").get(0).text();
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
    book.url = url;
    book.title = parse.select("#info > h1").get(0).text();
    book.author = parse.select("#info > p:nth-child(2)").text().split("者：")[1];
    book.intro = parse.select("#intro").html();
    book.cover = parse.select("#fmimg > img").get(0).absUrl("src");
    //加载章节列表
    var chapterList = new ArrayList();
//    var chapterListUrl = parse.select("#newlist > div > strong > a").get(0).absUrl("href");
//    var chapterListHtml = Jsoup.parse(http.get(chapterListUrl), chapterListUrl);
    var chapterListEl = parse.select("#list a");
    for(i=chapterListEl.size() - 1; i>= 0;i--){
        var chapterEl = chapterListEl.get(i);
        var chapter = new Chapter();
        chapter.title = chapterEl.text();
        chapter.url = chapterEl.absUrl("href");
        chapterList.add(chapter);
    }
    
    book.chapterList = chapterList;
    return book;
}

function getChapterContent(http,url){
    var html = http.get(url);
    return Jsoup.parse(html).select("#content").outerHtml();
}

            
        """.trimIndent(), 0, false
    )

    suspend fun testJavaScript() = withIo {
        if (!test) {
            return@withIo
        }
        val query = "哈利波特之学霸无敌"
//        val query = "哈利波特"
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
        Log.e("${book.source} 加载章节内容:${book.chapterList?.firstOrNull()?.title} $book")
        val chapter = book.chapterList?.firstOrNull()
        if (chapter != null) {
            val c = javaScript.execute<String>(getChapterContent, chapter.url)
            chapter.content = ChapterContent(chapter.url, chapter.bookUrl, c ?: "")
            Log.e("测试：${if (c.isNullOrBlank()) "失败" else "通过"} ${chapter.content} ")
        }
        Unit
    }
}