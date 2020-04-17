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

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import sjj.alog.Log;

public final class ParseTest {
    private static String source = "天籁小说";

    public static List<SearchResult> search(Http http, String query) throws Exception {
        List<SearchResult> results = new ArrayList<>();
        String baseUrl = "https://www.23txt.com/";
        String html = http.get(baseUrl + "search.php?keyword=" + URLEncoder.encode(query, "utf-8"));
        Document parse = Jsoup.parse(html, baseUrl);
        Elements bookListEl = parse.select("body > div.result-list > *");
        for (int i = 0; i < bookListEl.size(); i++) {
            Element bookEl = bookListEl.get(i);
            SearchResult result = new SearchResult();
            result.source = source;
            result.bookTitle = bookEl.select("a.result-game-item-title-link").text();
            result.bookUrl = bookEl.select("a.result-game-item-title-link").get(0).absUrl("href");
            result.bookAuthor = bookEl.select("> div.result-game-item-detail > div > p:nth-child(1) > span:nth-child(2)").get(0).text();
            result.latestChapter = bookEl.select("> div.result-game-item-detail > div > p:nth-child(4) > a").get(0).text();
            result.bookCover = bookEl.select("img.result-game-item-pic-link-img").get(0).absUrl("src");
            results.add(result);
        }
        return results;
    }

    public static Book getBookDetails(Http http, String url) {
        Document parse = Jsoup.parse(http.get(url), url);
        Book book = new Book();
        book.source = source;
        book.url = url;
        book.title = parse.select("#info > h1").text();
        book.author = parse.select("#info > p:nth-child(2)").text().split("者：")[1];
        book.intro = parse.select("#intro").text();
        book.cover = parse.select("#fmimg > img").get(0).absUrl("src");
        List<Chapter> chapterList = new ArrayList<>();

        Elements chapterListEl = parse.select("#list > dl a");
        for (int i = 0; i < chapterListEl.size(); i++) {
            Element chapterEl = chapterListEl.get(i);
            Chapter chapter = new Chapter();
            chapter.bookUrl = book.url;
            chapter.title = chapterEl.text();
            chapter.url = chapterEl.absUrl("href");
            chapterList.add(chapter);
        }
        book.chapterList = chapterList;
        return book;
    }

    public static String getBookChapterContent(Http http, String url) {
        String html = http.get(url);
        return Jsoup.parse(html).select("#content").html();
    }

    public static <R> R js(Function<ContextWrap, R> function) {
        return RhinosKt.runJs(function::apply);
    }

    public static void test() throws Exception {
        List<SearchResult> searchResults = search(HttpKt.getHttp(), "哈利波特");
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
