package com.sjianjun.test;

import com.sjianjun.test.bean.Book
import com.sjianjun.test.bean.Chapter
import com.sjianjun.test.bean.SearchResult;
import com.sjianjun.test.http.http
import org.jsoup.Jsoup
import java.net.URLEncoder
import kotlin.math.min


fun search(query: String): MutableList<SearchResult> {
    val queryMap = mutableMapOf<String, String>()
    queryMap["q"] = URLEncoder.encode(query, "utf-8")
    val resp = http.get("http://www.wenxuedu.com/modules/article/search.php", queryMap)
    val doc = Jsoup.parse(resp.body, resp.url)
    val elements = doc.select(".tuwen")
    val results = mutableListOf<SearchResult>()
    elements.forEach {
        val result = SearchResult()
        result.bookTitle = it.select("h1 a").text()
        result.bookUrl = it.select("h1 a")[0].absUrl("href")
            .replace("book/info", "html").replace(".html", "/index.html")
        result.bookAuthor = it.select("h2 em").text()
        results.add(result)
    }
    return results
}

fun getDetails(url: String): Book {
    val parse = Jsoup.parse(http.get(url).body, url);
    val book = Book()

    book.url = url
    book.title = parse.select("h1")[0].ownText()
        .replace("》", "").replace("《", "")
    book.author = parse.select("h1 > span").text().split(":")[1]

    book.intro = parse.select("#tuijian").html()
    //加载章节列表
    val chapterEls = parse.select(".ccss a");
    val chapterList = mutableListOf<Chapter>();
    chapterEls.forEach { chapterEl ->
        val chapter = Chapter()
        chapter.title = chapterEl.text();
        chapter.url = chapterEl.absUrl("href");
        chapterList.add(chapter);
    }
    book.chapterList = chapterList;
    return book
}

fun getChapterContent(url: String): String? {
    val parse = Jsoup.parse(http.get(url).body, url)
    return parse.select("#content").html()
}

fun ktTest() {
    val resultList = search("我的")
    println("搜索到结果数量：${resultList.size}")
    if (resultList.isEmpty()) {
        return
    }
    val selectBook = resultList[0]

    println("测试加载书籍：${selectBook.bookAuthor}")
    val ta = "${selectBook.bookTitle}_${selectBook.bookAuthor}"
    var details = getDetails(selectBook.bookUrl)
    println("搜索到章节数量：${details.chapterList?.size}")
    if (details.chapterList.isNullOrEmpty()) {
        return
    }
    if (ta != "${details.title}_${details.author}") {
        println("搜索结果书名与详情页不同1：${ta}==>${details.title}_${details.author}")
        return
    }

    details = getDetails(details.url)
    println("详情页再次加载搜索到章节数量：${details.chapterList?.size}")
    if (details.chapterList.isNullOrEmpty()) {
        return
    }
    if (ta != "${details.title}_${details.author}") {
        println("搜索结果书名与详情页不同2：${ta}==>${details.title}_${details.author}")
        return
    }

    val content = getChapterContent(details.chapterList?.first()!!.url)
    if (content.isNullOrEmpty()) {
        println("校验失败")
        return
    } else {
        println("搜索结果》》》》》》》》》》》》》》》》》》》》》》》》》》》》》》")
        println("搜索到结果数量：${resultList.size}")
        println("详情》》》》》》》》》》》》》》》》》》》》》》》》》》》》》》")
        println("书名：${details.title}")
        println("作者：${details.author}")
        println("链接：${details.url}")
        println("简介：${details.intro}")
        println("封面：${details.cover}")
        println("章节内容》》》》》》》》》》》》》》》》》》》》》》》》》》》》》》长度：${content.length}")
        println(content.subSequence(0, min(100, content.length)))
        println("校验成功")
        return
    }
}