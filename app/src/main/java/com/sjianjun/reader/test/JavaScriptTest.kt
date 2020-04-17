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
        parse.getElementById("").tagName()
        parse.getElementsByClass("").get(0).html()
        parse.select("atc > img").select("").text().split("：")
        parse.getElementsByTag("book")
        parse.child(0).ownText()
        parse.attr("content").replace("format=html5; url=", "")
        parse.getElementsByAttributeValue("", "")
        val children = parse.children()
        "".replace("m.", "")
    }

    val javaScript = JavaScript("起点中文网") {
        """
function search(http,query){
    var baseUrl = "https://www.qidian.com/";
    var html = http.get(baseUrl + "search?kw=" + URLEncoder.encode(query, "utf-8"));
    var parse = Jsoup.parse(html,baseUrl);
    var bookListEl = parse.select(".res-book-item");
    var results = new ArrayList();
    for (var i=0;i<bookListEl.size();i++){
        var bookEl = bookListEl.get(i);
        var result = new SearchResult();
        result.source = source;
        result.bookTitle = bookEl.select(".book-mid-info h4").text();
        result.bookUrl = bookEl.select(".book-mid-info h4 a").get(0).absUrl("href");
        result.bookAuthor = bookEl.select(".author a").get(0).text();
        result.bookCover = bookEl.select("img").get(0).absUrl("src");
        result.latestChapter = bookEl.select(".update a").get(0).text();
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
    var bookInfoEl = parse.select(".book-info").get(0);
    book.url = url;
    book.title = bookInfoEl.select("h1 em").text();
    book.author = bookInfoEl.select("h1 span a").text();
    book.intro = parse.select("#book-intro").text();
    book.cover = parse.select("#bookImg > img").get(0).absUrl("src");
    //加载章节列表
    var chapterList = new ArrayList();
    var chapterListEl = parse.select(".volume-wrap > .volume > .cf li a");
    if (chapterListEl.isEmpty()) {
        var bookId = bookInfoEl.select("#addBookBtn").attr("data-bookid");
        var chapterListUrl = "https://m.qidian.com/book/" + bookId + "/catalog";
        var chapterListHtml = http.get(chapterListUrl);
        var phoneChapterListEl = Jsoup.parse(chapterListHtml,chapterListUrl).select(".chapter-li-a");
        for (i = 0; i < phoneChapterListEl.size(); i++) {
            var chapterEl = phoneChapterListEl.get(i);
            var chapter = new Chapter();
            chapter.bookUrl = book.url;
            chapter.title = chapterEl.select("span").text();
            chapter.url = chapterEl.absUrl("href");
            chapterList.add(chapter);
        }

        try{
            //起点中文网章节列表暂时有点问题
            var lastChapterEl = parse.select(".update .detail .cf a").get(0);
            var chapter = new Chapter();
            chapter.bookUrl = book.url;
            chapter.title = lastChapterEl.text();
            chapter.url = lastChapterEl.absUrl("href");
            chapterList.add(chapter);
        }catch(error){
            Log.e(source+"最新章节列表解析失败")
        }

    } else {
        for(i=0; i<chapterListEl.size();i++){
            var chapterEl = chapterListEl.get(i);
            var chapter = new Chapter();
            chapter.title = chapterEl.text();
            chapter.url = chapterEl.absUrl("href");
            chapterList.add(chapter);
        }
    }
    book.chapterList = chapterList;
    return book;
}

function getChapterContent(http,url){
    var html = http.get(url);
    if (url.startsWith("https://m.qidian.com/")) {
        return Jsoup.parse(html).select("#chapterContent").html();
    } else {
        return Jsoup.parse(html).select(".main-text-wrap  div.read-content").html();
    }
}

            
        """.trimIndent()
    }

    suspend fun testJavaScript() = withIo {
//                val query = "诡秘之主"
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