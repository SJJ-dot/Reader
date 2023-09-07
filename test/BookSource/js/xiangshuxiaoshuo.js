function search(query) {
    var baseUrl = "https://www.ibiquge.la";
    var url = "/modules/article/waps.php?searchkey=" + URLEncoder.encode(query, "utf-8");
    var doc = get({baseUrl: baseUrl, url: url})

    var bookListEl = doc.select("tbody").get(0).select("tr");
    var results = new ArrayList();
    for (var i = 1; i < bookListEl.size(); i++) {
        var bookEl = bookListEl.get(i);
        var result = new SearchResult();
        result.source = source;
        result.bookTitle = bookEl.select("td").get(0).select("a").get(0).text();
        result.bookUrl = bookEl.select("td").get(0).select("a").get(0).absUrl("href");
        result.bookAuthor = bookEl.select("td").get(2).text();
        results.add(result);
    }
    return results;
}

function getDetails(url) {
    var doc = get({url: url});
    var book = new Book();
    book.url = url;
    book.title = doc.select("meta[property=\"og:novel:book_name\"]").attr("content");
    book.author = doc.select("meta[property=\"og:novel:author\"]").attr("content");
    book.intro = doc.select("meta[property=\"og:description\"]").attr("content");
    book.cover = doc.select("meta[property=\"og:image\"]").attr("content");
    //加载章节列表
    var children = doc.select("#list a");
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
    var doc = get({url: url});
    return doc.select("#content").html()
}