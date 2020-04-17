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
    fun showName() {
        val parse = Jsoup.parse("")
        parse.getElementsByTag("").get(0).baseUri()
        parse.getElementById("").tagName()
        parse.getElementsByClass("").get(0).html()
        parse.select("atc > img").select("").text().split("：")
        parse.getElementsByTag("book")
        parse.child(0).ownText()
        parse.attr("content").replace("format=html5; url=", "")
        parse.getElementsByAttributeValue("", "")
        val children = parse.children()
        "".replace("m.", "")
//        StringUtil.resolve()
    }

    val javaScript = JavaScript("天籁小说") {
        """
function search(http,query){
    var baseUrl = "https://www.23txt.com/";
    var html = http.get(baseUrl + "search.php?keyword=" + URLEncoder.encode(query, "utf-8"));
    var parse = Jsoup.parse(html, baseUrl);
    var bookListEl = parse.select("body > div.result-list > *");
    var results = new ArrayList();
    for (var i=0;i<bookListEl.size();i++){
        var bookEl = bookListEl.get(i);
        var result = new SearchResult();
        result.source = source;
        result.bookTitle = bookEl.select("a.result-game-item-title-link").text();
        result.bookUrl = bookEl.select("a.result-game-item-title-link").get(0).absUrl("href");
        result.bookAuthor = bookEl.select("> div.result-game-item-detail > div > p:nth-child(1) > span:nth-child(2)").get(0).text();
        result.bookCover = bookEl.select("> div.result-game-item-detail > div > p:nth-child(4) > a").get(0).text();
        result.latestChapter = bookEl.select("img.result-game-item-pic-link-img").get(0).absUrl("src");
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
    book.title = parse.select("#info > h1").text();
    book.author = parse.select("#info > p:nth-child(2)").text().split("者：")[1];
    book.intro = parse.select("#intro").text();
    book.cover = parse.select("#fmimg > img").get(0).absUrl("src");
    //加载章节列表
    var chapterList = new ArrayList();
    var chapterListEl = parse.select("#list > dl a");
    for(i=0; i<chapterListEl.size();i++){
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
    return Jsoup.parse(html).select("#content").html();
}

            
        """.trimIndent()
    }

    suspend fun testJavaScript() = withIo {
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