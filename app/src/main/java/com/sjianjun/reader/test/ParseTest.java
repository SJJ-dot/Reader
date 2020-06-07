package com.sjianjun.reader.test;

import com.sjianjun.reader.bean.Book;
import com.sjianjun.reader.bean.Chapter;
import com.sjianjun.reader.bean.SearchResult;
import com.sjianjun.reader.http.Http;
import com.sjianjun.reader.http.HttpKt;
import com.sjianjun.reader.rhino.ContextWrap;
import com.sjianjun.reader.rhino.RhinosKt;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import sjj.alog.Log;

public final class ParseTest {
    private static boolean test = false;
    private static String source = "黑岩网";
    private static String baseUrl = "https://www.heiyan.com/";

    public static List<SearchResult> search(Http http, String query) throws Exception {

//        Map<String, String> queryMap = new HashMap<>();
//        queryMap.put("key", URLEncoder.encode(query, "utf-8"));
//        String html = http.post(baseUrl + "case.php?m=search", queryMap);
        String html = http.get("https://search.heiyan.com/web/search?highlight=false&page=1&queryString=" + URLEncoder.encode(query, "utf-8"));
        Document parse = Jsoup.parse(html, baseUrl);
        Elements bookListEl = parse.select("#search > ul > li");
        List<SearchResult> results = new ArrayList<>();
        for (int i = 0; i < bookListEl.size(); i++) {
            Element bookEl = bookListEl.get(i);
            SearchResult result = new SearchResult();
            result.source = source;
            result.bookTitle = bookEl.selectFirst(":nth-child(1) > div.right > p.info > a").text();
            result.bookUrl = bookEl.selectFirst(":nth-child(1) > div.right > p.info > a").absUrl("href");
            result.bookAuthor = bookEl.selectFirst(":nth-child(1) > div.right > p.info > span:nth-child(4) > a").text();
            result.bookCover = bookEl.select(":nth-child(1) > div.left > a > img").get(0).absUrl("src");
//            result.latestChapter = bookEl.selectFirst(":nth-child(1) > span.s3 > a").text();
            results.add(result);
        }
        return results;
    }

    public static Book getBookDetails(Http http, String url) throws UnsupportedEncodingException {
        Document parse = Jsoup.parse(http.get(url), url);
        Book book = new Book();
        book.source = source;
        book.url = url;
        book.title = parse.selectFirst("body > div.wrap > div > div > div.c-left > div.mod.pattern-cover-detail > div.hd > h1").text();
        book.author = parse.selectFirst("body > div.wrap > div > div > div.c-right > div.mod.pattern-cover-author > div > div.author-zone.column-2 > div.right > a > strong").text();
        book.intro = parse.selectFirst("body > div.wrap > div > div > div.c-left > div.mod.pattern-cover-detail > div.bd.column-2.bd-p > div.right > div.summary.min-summary-height > pre.note").outerHtml();
        book.cover = parse.selectFirst("#voteStaff > div.pic > a > img").absUrl("src");
        List<Chapter> chapterList = new ArrayList<>();


        String chapterListUrl = parse.selectFirst("#voteList > div.buttons.clearfix > a.index").absUrl("href");
        Document chapterListHtml = Jsoup.parse(http.get(chapterListUrl), chapterListUrl);
        Elements chapterListEl = chapterListHtml.select("body > div.wrap > div > div > div.c-left > div > div.bd > ul a");
//        Elements chapterListEl = parse.select("#list > dl > *");
        for (int i = chapterListEl.size() - 1; i >= 0; i--) {
            Element chapterA = chapterListEl.get(i);
            Chapter chapter = new Chapter();
            chapter.bookUrl = book.url;
            chapter.title = chapterA.text();
            chapter.url = chapterA.absUrl("href");
            chapterList.add(0,chapter);
        }
        book.chapterList = chapterList;
        return book;
    }

    public static String getBookChapterContent(Http http, String url) {
        String html = http.get(url);
        return Jsoup.parse(html).selectFirst(".bd .page-content").outerHtml();
    }

    public static <R> R js(Function<ContextWrap, R> function) {
        return RhinosKt.runJs(function::apply);
    }

    public static void test() throws Exception {
        if (!test) {
            return;
        }
        List<SearchResult> searchResults = search(HttpKt.getHttp(), "校花的修仙强者");
        Log.e(searchResults);
        if (searchResults.isEmpty()) {
            return;
        }
        Book book = getBookDetails(HttpKt.getHttp(), searchResults.get(0).bookUrl);
        Log.e(book);
        if (book == null || book.chapterList.isEmpty()) {
            return;
        }
        Log.e(book.chapterList.get(book.chapterList.size() - 1));
        String content = getBookChapterContent(HttpKt.getHttp(), book.chapterList.get(book.chapterList.size() - 1).url);
        Log.e(content);
    }

}
