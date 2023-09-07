function search(query) {
    var baseUrl = "http://www.lianjianxsw.com/search";
    var resp = http.body(baseUrl, JSON.stringify({"keyword": query}));

    var body = eval("(" + resp.body + ")");
    var bookListEl = body["data"];
    Log.e(">>>>>bookListEl Size " + bookListEl.length + ">>>>>>>>>>>>>>>>>>>>")
    var results = new ArrayList();
    for (var i = 0; i < bookListEl.length; i++) {
        var bookEl = bookListEl[i];
        Log.e(">>>>>" + i + ">>>>>>>>>>>>>>>>>>>>")
        Log.e("bookEl>>>>")
        Log.e(bookEl)
        var result = new SearchResult();
        Log.e("bookTitle>>>>")
        result.bookTitle = bookEl["name"];
        Log.e(result.bookTitle)
        Log.e("bookUrl>>>>")
        result.bookUrl = "http://www.lianjianxsw.com/bookInfo?bookid=" + bookEl["_id"]
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
    var parse = eval("(" + http.get(url).body + ")")["data"]["book"];
    var book = new Book();
    book.url = url;
    Log.e("title>>>>")
    book.title = parse["name"];
    Log.e(book.title)
    Log.e("author>>>>")
    book.author = parse["author"];
    Log.e(book.author)
    Log.e("intro>>>>")
    book.intro = parse["intro"];
    Log.e(book.intro)
    Log.e("cover>>>>")
    book.cover = "http://www.lianjianxsw.com/pic/" + parse["_id"] + ".jpg";
    Log.e(book.cover)

    var chapterListUrl = "http://www.lianjianxsw.com/getCataLogs?bookid=" + parse["_id"] + "&page=1&limit=" + parse["total_num"];
    var parseChapter = eval("(" + http.get(chapterListUrl).body + ")");
    //加载章节列表
    var children = parseChapter["data"]["list"];
    Log.e("章节列表数量：" + children.length + ">>>>>>>>>>>>>>>>>>>>")
    var chapterList = new ArrayList();
    for (i = 0; i < children.length; i++) {
        var chapterEl = children[i];
        var chapter = new Chapter();
        Log.e("chapter.title>>>>")
        chapter.title = chapterEl["name"];
        Log.e(chapter.title)
        Log.e("chapter.url>>>>")
        // http://www.lianjianxsw.com/getContent?bookid=@get:{bid}&chapterid={{$._id}}
        chapter.url = "http://www.lianjianxsw.com/getContent?bookid=" + parse["_id"] + "&chapterid=" + chapterEl["_id"];
        Log.e(chapter.title)
        chapterList.add(chapter);
    }
    book.chapterList = chapterList;
    return book;
}

function getChapterContent(url) {
    var parse = eval("(" + http.get(url).body + ")")["data"]["chapterInfo"];
    return AesUtil.decrypt(parse["content"], "6CE93717FBEA3E4F", "AES/CBC/NoPadding", "6CE93717FBEA3E4F").trim().replace("###$$$", "");
}