package com.sjianjun.reader.test

import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.ChapterContent
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.bean.JavaScript.Func.*
import com.sjianjun.reader.bean.SearchResult
import sjj.alog.Log

/**
 * 已解析：biquge5200.cc、古古小说网（55小说网）
 * 不支持：乐安宣书网(搜索结果没有作者)、
 */
object JavaScriptTest {
    var test = false

    val javaScript = JavaScript(
        "番茄小说cc",
        """
var hostUrl = "http://www.fqxsw.cc/";
function search(http,query){
    var baseUrl = "http://www.fqxsw.cc/";
    var html = http.get(baseUrl+"modules/article/search.php?searchkey=" + URLEncoder.encode(query, "gbk"));
    var parse = Jsoup.parse(html, baseUrl);
    var bookListEl = parse.select("body > div.container.body-content > div.panel.panel-default > table > tbody > tr");
    var results = new ArrayList();
    for (var i=1;i<bookListEl.size();i++){
        var bookEl = bookListEl.get(i);
        var result = new SearchResult();
        result.source = source;
        result.bookTitle = bookEl.selectFirst("> td:nth-child(1) > a").text();
        result.bookUrl = bookEl.selectFirst("> td:nth-child(1) > a").absUrl("href");
        result.bookAuthor = bookEl.selectFirst("> td:nth-child(3)").text();
        result.latestChapter = bookEl.selectFirst("> td:nth-child(2) > a").text();
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
    book.title = parse.selectFirst("body > div.container.body-content > div:nth-child(2) > div > div > div.col-md-10 > h1").text();
    book.author = parse.selectFirst("body > div.container.body-content > div:nth-child(2) > div > div > div.col-md-10 > p.booktag > a:nth-child(1)").text();
    book.intro = parse.selectFirst("#bookIntro").outerHtml();
    book.cover = parse.selectFirst("body > div.container.body-content > div:nth-child(2) > div > div > div.col-md-2.col-xs-4.hidden-xs > img").absUrl("src");
    //加载章节列表
    var chapterList = new ArrayList();
    var chapterListEl = parse.select("#list-chapterAll a");
    for(i=chapterListEl.size() - 1; i>= 0;i--){
        var chapterEl = chapterListEl.get(i);
        var chapterA = chapterEl.selectFirst("a");
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
    return Jsoup.parse(html).selectFirst("#htmlContent").outerHtml();
}

            
        """.trimIndent(), 0, false
    )

    suspend fun testJavaScript() = withIo {
        if (!test) {
            return@withIo
        }
        val query = "圣光"
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