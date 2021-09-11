package com.sjianjun.reader.test

import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.BuildConfig
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
        "biquges_com",
        """
var hostUrl = "https://www.biquges.com/";
function search(http,query){
    var baseUrl = hostUrl;
    var map = new HashMap();
    map.put("searchtype", "articlename")
    map.put("searchkey",URLEncoder.encode(query,"utf-8"))
    var html = http.post(baseUrl+"modules/article/search.php",map);
    var parse = Jsoup.parse(html, baseUrl);
    var bookListEl = parse.select("#nr");
    var results = new ArrayList();
    for (var i=1;i<bookListEl.size();i++){
        var bookEl = bookListEl.get(i);
        var result = new SearchResult();
        result.source = source;
        result.bookTitle = bookEl.selectFirst("> td:nth-child(1) > a").text();
        result.bookUrl = bookEl.selectFirst("> td:nth-child(1) > a").absUrl("href");
        result.bookAuthor = bookEl.selectFirst("> td:nth-child(3)").text();
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
    book.title =  parse.selectFirst("[property=og:novel:book_name]").attr("content");
    book.author = parse.selectFirst("[property=og:novel:author]").attr("content");
    book.intro = parse.selectFirst("#intro").outerHtml();
    book.cover = parse.selectFirst("#fmimg > img").absUrl("src");
    //加载章节列表
    var children = parse.selectFirst("#list dl").children();
    var chapterList = new ArrayList();
    for(i=children.size()-1; i>=0; i--){
        var chapterEl = children.get(i);
        if(chapterEl.tagName() == "dt"){
            break;
        }
        var chapter = new Chapter();
        chapter.title = chapterEl.child(0).text();
        chapter.url = chapterEl.child(0).absUrl("href");
        chapterList.add(0,chapter);
    }
    book.chapterList = chapterList;
    return book;
}

function getChapterContent(http,url){
    var html = http.get(url);
    return Jsoup.parse(html).selectFirst("#content").outerHtml();
}

            
        """.trimIndent(), 1
    )

    suspend fun testJavaScript() = withIo {
        if (!test || !BuildConfig.DEBUG) {
            return@withIo
        }
        val query = "诡秘之主"
//        val query = "哈利波特"
        Log.e("${javaScript.source} 搜索 $query")
        val result = javaScript.execute<List<SearchResult>>(search, query)
        if (result.isNullOrEmpty()) {
            Log.e("${javaScript.source} 搜索结果为空")
            return@withIo
        }
        val first = result.first()
        Log.e("${first.source} bookCount:${result.size} 加载书籍详情 ${first.bookTitle} ${first.bookUrl}")
        val book = javaScript.execute<Book>(getDetails, first.bookUrl)
        if (book == null) {
            Log.e("${first.source}  书籍加载失败")
            return@withIo
        }
        Log.e("${book.source} chapterCount:${book.chapterList?.size} 加载章节内容:${book.chapterList?.firstOrNull()?.title} $book")
        val chapter = book.chapterList?.firstOrNull()
        if (chapter != null) {
            val c = javaScript.execute<String>(getChapterContent, chapter.url)
            chapter.content = ChapterContent(chapter.url, chapter.bookUrl, c ?: "")
            Log.e("测试：${if (c.isNullOrBlank()) "失败" else "通过"} ${chapter.content} ")
        }
        Unit
    }
}