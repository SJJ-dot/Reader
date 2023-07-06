function search(query) {
    var baseUrl = "https://www.qidian.com/";
    var url = "soushu/" + URLEncoder.encode(query, "utf-8") + ".html";
    var doc = get({baseUrl: baseUrl, url: url})

    var bookListEl = doc.select(".res-book-item");
    var results = new ArrayList();
    for (var i = 0; i < bookListEl.size(); i++) {
        var bookEl = bookListEl.get(i);
        var result = new SearchResult();
        result.bookTitle = bookEl.select(".book-info-title").get(0).select("a").get(0).text();
        result.bookUrl = "https://m.qidian.com/book/" + (bookEl.select("a[data-bid]").attr("data-bid"));
        result.bookAuthor = bookEl.select(".author").select(".author > :nth-child(2)").get(0).text();
        results.add(result);
    }
    return results;
}

function getDetails(url) {
    var doc = get({url: url});
    var book = new Book();
    book.url = url;
    Log.e(">>>>>book.title")
    book.title = doc.select("meta[property=\"og:novel:book_name\"]").attr("content");
    Log.e(book.bookTitle)
    book.author = doc.select("meta[property=\"og:novel:author\"]").attr("content");
    Log.e(book.author)
    book.intro = doc.select("meta[property=\"og:description\"]").attr("content");
    Log.e(book.intro)
    book.cover = doc.select("meta[property=\"og:image\"]").attr("content");
    Log.e(book.cover)
    var chapterListUrl = doc.select("#details-menu").get(0).absUrl("href");
    var chapterListEl = get({url:chapterListUrl}).select(".y-list__content a");
    Log.e(chapterListEl.size())
    var chapterList = new ArrayList();
    for (i = 0; i < chapterListEl.size(); i++) {
        var chapterEl = chapterListEl.get(i);
        var chapter = new Chapter();
        chapter.title = chapterEl.text();
        Log.e(chapter.title)
        chapter.url = chapterEl.absUrl("href");
        Log.e(chapter.url)
        chapterList.add(chapter);
    }
    book.chapterList = chapterList;
    return book;
}

function getChapterContent(url) {
    var doc = get({url: url});
    return doc.select("main").html()
}