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
    private static String source = "番茄小说cc";
    private static String baseUrl = "http://www.fqxsw.cc/";

    public static List<SearchResult> search(Http http, String query) throws Exception {

//        Map<String, String> queryMap = new HashMap<>();
//        queryMap.put("searchkey", URLEncoder.encode(query, "gbk"));
//        String html = http.post(baseUrl + "modules/article/search.php", queryMap);
        String html = http.get(baseUrl+"modules/article/search.php?searchkey=" + URLEncoder.encode(query, "gbk"));
        Document parse = Jsoup.parse(html, baseUrl);
        Elements bookListEl = parse.select("body > div.container.body-content > div.panel.panel-default > table > tbody > tr");
        List<SearchResult> results = new ArrayList<>();
        for (int i = 1; i < bookListEl.size(); i++) {
            Element bookEl = bookListEl.get(i);
            SearchResult result = new SearchResult();
            result.source = source;
            result.bookTitle = bookEl.selectFirst("> td:nth-child(1) > a").text();
            result.bookUrl = bookEl.selectFirst("> td:nth-child(1) > a").absUrl("href");
            result.bookAuthor = bookEl.selectFirst("> td:nth-child(3)").text();
//            result.bookCover = bookEl.select(":nth-child(1) > div.left > a > img").get(0).absUrl("src");
            result.latestChapter = bookEl.selectFirst("> td:nth-child(2) > a").text();
            results.add(result);
        }
        return results;
    }

    public static Book getBookDetails(Http http, String url) throws UnsupportedEncodingException {
        Document parse = Jsoup.parse(http.get(url), url);
        Book book = new Book();
        book.source = source;
        book.url = url;
        book.title = parse.selectFirst("body > div.container.body-content > div:nth-child(2) > div > div > div.col-md-10 > h1").text();
        book.author = parse.selectFirst("body > div.container.body-content > div:nth-child(2) > div > div > div.col-md-10 > p.booktag > a:nth-child(1)").text();
        book.intro = parse.selectFirst("#bookIntro").outerHtml();
        book.cover = parse.selectFirst("body > div.container.body-content > div:nth-child(2) > div > div > div.col-md-2.col-xs-4.hidden-xs > img").absUrl("src");
        List<Chapter> chapterList = new ArrayList<>();

        Elements chapterListEl = parse.select("#list-chapterAll a");
        for (int i = chapterListEl.size() - 1; i >= 0; i--) {
            Element chapterEl = chapterListEl.get(i);
//            if ("dt".equals(chapterEl.tagName())) {
//                break;
//            }
            Element chapterA = chapterEl.selectFirst("a");
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
        return Jsoup.parse(html).selectFirst("#htmlContent").outerHtml();
    }

    public static <R> R js(Function<ContextWrap, R> function) {
        return RhinosKt.runJs(function::apply);
    }

    public static void test() throws Exception {
        if (!test) {
            return;
        }
        List<SearchResult> searchResults = search(HttpKt.getHttp(), "圣光");
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
