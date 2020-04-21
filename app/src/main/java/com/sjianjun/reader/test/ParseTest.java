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
    private static boolean test = false;
    private static String source = "纵横中文网";

    public static List<SearchResult> search(Http http, String query) throws Exception {
        String baseUrl = "http://search.zongheng.com/";
        String html = http.get(baseUrl + "s?keyword=" + URLEncoder.encode(query, "utf-8"));
        Document parse = Jsoup.parse(html, baseUrl);
        Elements bookListEl = parse.select("div.search-tab > div.search-result-list.clearfix");
        List<SearchResult> results = new ArrayList<>();
        for (int i = 0; i < bookListEl.size(); i++) {
            Element bookEl = bookListEl.get(i);
            SearchResult result = new SearchResult();
            result.source = source;
            result.bookTitle = bookEl.select(".tit a").text();
            result.bookUrl = bookEl.select(".tit a").get(0).absUrl("href");
            result.bookAuthor = bookEl.select(".bookinfo a:nth-child(1)").text();
            result.bookCover = bookEl.select(".imgbox img").get(0).absUrl("src");
//            result.latestChapter = bookEl.select("> div.result-game-item-detail > div > p:nth-child(4) > a").get(0).text();
            results.add(result);
        }
        return results;
    }

    public static Book getBookDetails(Http http, String url) {
        Document parse = Jsoup.parse(http.get(url), url);
        Book book = new Book();
        book.source = source;
        book.url = url;
        book.title = parse.select("div.book-info > div.book-name").get(0).ownText();
        book.author = parse.select("div.book-author > div.au-name > a").text();
        book.intro = parse.select(".book-dec > p").text();
        book.cover = parse.select("div.book-img.fl > img").get(0).absUrl("src");
        List<Chapter> chapterList = new ArrayList<>();

        String chapterListUrl = parse.select(".all-catalog").get(0).absUrl("href");
        Document chapterListHtml = Jsoup.parse(http.get(chapterListUrl), chapterListUrl);
        Elements chapterListEl = chapterListHtml.select("ul.chapter-list.clearfix a");
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
        return Jsoup.parse(html).select(".content").html();
    }

    public static <R> R js(Function<ContextWrap, R> function) {
        return RhinosKt.runJs(function::apply);
    }

    public static void test() throws Exception {
        if (!test) {
            return;
        }
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
