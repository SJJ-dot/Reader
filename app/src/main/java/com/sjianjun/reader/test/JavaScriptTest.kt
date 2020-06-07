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
        "黑岩网",
        """
var baseUrl = "https://www.heiyan.com/";
function search(http,query){
    var html = http.get("https://search.heiyan.com/web/search?highlight=false&page=1&queryString=" + URLEncoder.encode(query, "utf-8"));
    var json = eval("("+html+")");
    var bookListEl = json["data"]["content"];
    var results = new ArrayList();
    for (var i=0;i<bookListEl.length;i++){
        var bookEl = bookListEl[i];
        var result = new SearchResult();
        result.source = source;
        result.bookTitle = bookEl["name"];
        result.bookUrl = baseUrl+"book/"+bookEl["id"];
        result.bookAuthor = bookEl["authorname"];
        result.latestChapter = bookEl["lastchaptername"];
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
    book.title = parse.selectFirst("body > div.wrap > div > div > div.c-left > div.mod.pattern-cover-detail > div.hd > h1").text();
    book.author = parse.selectFirst("body > div.wrap > div > div > div.c-right > div.mod.pattern-cover-author > div > div.author-zone.column-2 > div.right > a > strong").text();
    book.intro = parse.selectFirst("body > div.wrap > div > div > div.c-left > div.mod.pattern-cover-detail > div.bd.column-2.bd-p > div.right > div.summary.min-summary-height > pre.note").html();
    book.cover = parse.selectFirst("#voteStaff > div.pic > a > img").absUrl("src");
    //加载章节列表
    var chapterListUrl = parse.selectFirst("#voteList > div.buttons.clearfix > a.index").absUrl("href");
    var chapterListHtml = Jsoup.parse(http.get(chapterListUrl), chapterListUrl);
    var chapterList = new ArrayList();
    var chapterListEl = chapterListHtml.select("body > div.wrap > div > div > div.c-left > div > div.bd > ul a");
    for(i=chapterListEl.size() - 1; i>= 0;i--){
        var chapterA = chapterListEl.get(i);
        var chapter = new Chapter();
        chapter.title = chapterA.text();
        chapter.url = chapterA.absUrl("href");
        chapterList.add(0,chapter);
    }
    
    book.chapterList = chapterList;
    return book;
}

function getChapterContent(http,url){
    var html = http.get(url);
    return Jsoup.parse(html).select(".bd .page-content").outerHtml();
}

            
        """.trimIndent(), 0, false
    )

    suspend fun testJavaScript() = withIo {
        if (!test) {
            return@withIo
        }
        val query = "史上最强炼气期"
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