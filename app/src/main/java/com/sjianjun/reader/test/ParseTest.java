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
    private static String source = "盗梦人小说";
    private static String baseUrl = "http://www.cxbz958.com/";

    public static List<SearchResult> search(Http http, String query) throws Exception {

//        Map<String, String> queryMap = new HashMap<>();
//        queryMap.put("searchkey", URLEncoder.encode(query, "utf-8"));
//        String html = http.post(baseUrl + "search.aspx?bookname", queryMap);
        String html = http.get(baseUrl + "s.php?ie=utf-8&q=" + URLEncoder.encode(query, "utf-8"));

        Document parse = Jsoup.parse(html, baseUrl);
        Elements bookListEl = parse.select(".bookbox");
        List<SearchResult> results = new ArrayList<>();
        for (int i = 0; i < bookListEl.size(); i++) {
            Element bookEl = bookListEl.get(i);
            SearchResult result = new SearchResult();
            result.source = source;
            result.bookTitle = bookEl.select("div.bookinfo > h4 > a").text();
            result.bookUrl = bookEl.select("div.bookinfo > h4 > a").get(0).absUrl("href");
            result.bookAuthor = bookEl.select("div.bookinfo > div.author").text().replace("作者：", "");
            result.bookCover = bookEl.select("div.bookimg > a > img").get(0).absUrl("src");
            result.latestChapter = bookEl.select("div.bookinfo > div.update > a").get(0).text();
            results.add(result);
        }
        return results;
    }

    public static Book getBookDetails(Http http, String url) throws UnsupportedEncodingException {
        Document parse = Jsoup.parse(http.get(url), url);
        Book book = new Book();
        book.source = source;
        book.url = url;
        book.title = parse.select("body > div.book > div.info > h2").get(0).text();
        book.author = parse.select("body > div.book > div.info > div.small > span:nth-child(1)").text().replace("作者：", "");
        book.intro = parse.select("body > div.book > div.info > div.intro").html();
        book.cover = parse.select("body > div.book > div.info > div.cover > img").get(0).absUrl("src");
        List<Chapter> chapterList = new ArrayList<>();


//        String chapterListUrl = parse.select("#newlist > div > strong > a").get(0).absUrl("href");
//        Document chapterListHtml = Jsoup.parse(http.post(baseUrl+"ashx/zj.ashx",queryMap), url);
        Elements chapterListEl = parse.select("body > div.listmain > dl > *");
        for (int i = chapterListEl.size() - 1; i >= 0; i--) {
            if (chapterListEl.get(i).tagName().equals("dt")) {
                break;
            }
            Element chapterEl = chapterListEl.get(i).child(0);
            Chapter chapter = new Chapter();
            chapter.bookUrl = book.url;
            chapter.title = chapterEl.text();
            chapter.url = chapterEl.absUrl("href");
            chapterList.add(0,chapter);
        }
        book.chapterList = chapterList;
        return book;
    }

    public static String getBookChapterContent(Http http, String url) {
        String html = http.get(url);
        return Jsoup.parse(html).select("#content").outerHtml();
    }

    public static <R> R js(Function<ContextWrap, R> function) {
        return RhinosKt.runJs(function::apply);
    }

    public static void test() throws Exception {
        if (!test) {
            return;
        }
        List<SearchResult> searchResults = search(HttpKt.getHttp(), "诡秘之主");
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
