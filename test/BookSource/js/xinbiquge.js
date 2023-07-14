function search(query) {
    var baseUrl = "https://www.367x.com/modules/article/search.php";
    var map = new HashMap();
    map.put("searchkey", URLEncoder.encode(query, "gbk"))
    var resp = http.post(baseUrl, map);
    var parse = Jsoup.parse(resp.body, resp.url);
    if (baseUrl == resp.url) {
        var bookListEl = parse.select(".txt-list li");
        Log.e(">>>>>bookListEl Size " + bookListEl.size() + ">>>>>>>>>>>>>>>>>>>>");
        var results = new ArrayList();
        for (var i = 1; i < bookListEl.size(); i++) {
            var bookEl = bookListEl.get(i);
            Log.e(bookEl.tagName() + ">>>>>" + i + ">>>>>>>>>>>>>>>>>>>>")
            Log.e("bookEl>>>>")
            Log.e(bookEl)
            var result = new SearchResult();
            Log.e("bookTitle>>>>")
            result.bookTitle = bookEl.select("a").get(0).text();
            Log.e(result.bookTitle)
            Log.e("bookUrl>>>>")
            result.bookUrl = bookEl.select("a").get(0).absUrl("href");
            Log.e(result.bookUrl)
            Log.e("bookAuthor>>>>")
            result.bookAuthor = bookEl.select(".s4").get(0).text();
            Log.e(result.bookAuthor)
            results.add(result);
            Log.e(bookEl.tagName() + "<<<<<" + i + "<<<<<<<<<<<<<<<<<<<<")
        }
        return results;
    } else {
        var results = new ArrayList();
        var result = new SearchResult();
        Log.e("bookTitle>>>>")
        result.bookTitle = parse.selectFirst("[property=og:title]").attr("content")
        Log.e(result.bookTitle)
        Log.e("bookUrl>>>>")
        result.bookUrl = parse.selectFirst("[property=og:url]").attr("content")
        Log.e(result.bookUrl)
        Log.e("bookAuthor>>>>")
        result.bookAuthor = parse.selectFirst("[property=og:novel:author]").attr("content");
        Log.e(result.bookAuthor)
        results.add(result);
        return results;
    }



}

/**
 * 书籍详情[JavaScript.source]
 */
function getDetails(url) {
    var parse = Jsoup.parse(http.get(url).body, url);
    var book = new Book();
    book.source = source;
    book.url = url;
    Log.e("title>>>>")
    book.title = parse.selectFirst("[property=og:title]").attr("content");
    Log.e(book.title)
    Log.e("author>>>>")
    book.author = parse.selectFirst("[property=og:novel:author]").attr("content");
    Log.e(book.author)
    Log.e("intro>>>>")
    book.intro = parse.selectFirst("[property=og:description]").attr("content");
    Log.e(book.intro)
    Log.e("cover>>>>")
    book.cover = parse.selectFirst("[property=og:image]").absUrl("content");
    Log.e(book.cover)
    //加载章节列表
    var children = parse.select("#section-list a");
    Log.e("章节列表数量：" + children.size())
    var chapterList = new ArrayList();
    for (i = 0; i < children.size(); i++) {
        var chapterEl = children.get(i);
        var chapter = new Chapter();
        Log.e("chapter.title>>>>")
        chapter.title = chapterEl.text();
        Log.e(chapter.title)
        Log.e("chapter.url>>>>")
        chapter.url = chapterEl.absUrl("href");
        Log.e(chapter.title)
        chapterList.add(chapter);
    }
    book.chapterList = chapterList;
    return book;
}

function getChapterContent(url) {
    var html = http.get(url).body;
    return Jsoup.parse(html).selectFirst("#content").outerHtml();
}