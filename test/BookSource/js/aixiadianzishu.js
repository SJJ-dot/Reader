function search(query) {
    var resp = http.get("http://api.aixdzs.com/book/search?query=" + URLEncoder.encode(query, "utf-8"));

    var body = eval("(" + resp.body + ")");
    var bookListEl = body["books"];
    Log.e(">>>>>bookListEl Size " + bookListEl.length + ">>>>>>>>>>>>>>>>>>>>")
    var results = new ArrayList();
    for (var i = 0; i < bookListEl.length; i++) {
        var bookEl = bookListEl[i];
        Log.e(">>>>>" + i + ">>>>>>>>>>>>>>>>>>>>")
        Log.e("bookEl>>>>")
        Log.e(bookEl)
        var result = new SearchResult();
        Log.e("bookTitle>>>>")
        result.bookTitle = bookEl["title"];
        Log.e(result.bookTitle)
        Log.e("bookUrl>>>>")
        result.bookUrl = "http://api.aixdzs.com/book/" + bookEl["_id"]
        Log.e(result.bookUrl)
        Log.e("bookAuthor>>>>")
        result.bookAuthor = bookEl["author"];
        Log.e(result.bookAuthor)
        results.add(result);
        Log.e("<<<<<" + i + "<<<<<<<<<<<<<<<<<<<<")
    }
    return results;

}

/**
 * 书籍详情[JavaScript.source]
 */
function getDetails(url) {
    var parse = eval("(" + http.get(url).body + ")");
    var book = new Book();
    book.url = url;
    Log.e("title>>>>")
    book.title = parse["title"];
    Log.e(book.title)
    Log.e("author>>>>")
    book.author = parse["author"];
    Log.e(book.author)
    Log.e("intro>>>>")
    book.intro = parse["longIntro"].replace(/\r\n/g, "<br>").replace(/\n/g, "<br>");
    Log.e(book.intro)
    Log.e("cover>>>>")
    book.cover = "https://img22.aixdzs.com/" + parse["cover"];
    Log.e(book.cover)

    var chapterListUrl = "http://api.aixdzs.com/content/" + parse["_id"] + "?view=chapter";
    var parseChapter = eval("(" + http.get(chapterListUrl).body + ")");
    //加载章节列表
    var children = parseChapter["mixToc"]["chapters"];
    Log.e("章节列表数量：" + children.length + ">>>>>>>>>>>>>>>>>>>>")
    var chapterList = new ArrayList();
    for (i = 0; i < children.length; i++) {
        var chapterEl = children[i];
        var chapter = new Chapter();
        Log.e("chapter.title>>>>")
        chapter.title = chapterEl["title"];
        Log.e(chapter.title)
        Log.e("chapter.url>>>>")
        // http://www.lianjianxsw.com/getContent?bookid=@get:{bid}&chapterid={{$._id}}
        chapter.url = "http://api.aixdzs.com/chapter/" + chapterEl["link"];
        Log.e(chapter.title)
        chapterList.add(chapter);
    }
    book.chapterList = chapterList;
    return book;
}

function getChapterContent(url) {
    var parse = eval("(" + http.get(url).body + ")");
    return parse["chapter"]["body"].replace(/\r\n/g, "<br>").replace(/\n/g, "<br>");
}