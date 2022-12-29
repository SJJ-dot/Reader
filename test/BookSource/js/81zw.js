function search(query){
    var baseUrl = "https://www.81zw.com/search.php?keyword=";
    var html = http.get(baseUrl + URLEncoder.encode(query, "utf-8")).body;
    var parse = Jsoup.parse(html, baseUrl);
    var bookListEl = parse.select(".result-item.result-game-item");
    Log.e(">>>>>bookListEl Size "+bookListEl.size()+">>>>>>>>>>>>>>>>>>>>")
    var results = new ArrayList();
    for (var i=0;i<bookListEl.size();i++){
        var bookEl = bookListEl.get(i);
        Log.e(bookEl.tagName()+">>>>>"+i+">>>>>>>>>>>>>>>>>>>>")
        Log.e("bookEl>>>>")
        Log.e(bookEl)
        var result = new SearchResult();
        Log.e("bookTitle>>>>")
        result.bookTitle = bookEl.selectFirst(".result-game-item-title-link").text();
        Log.e(result.bookTitle)
        Log.e("bookUrl>>>>")
        result.bookUrl = bookEl.selectFirst(".result-game-item-title-link").absUrl("href");
        Log.e(result.bookUrl)
        Log.e("bookAuthor>>>>")
        result.bookAuthor = bookEl.select("div.result-game-item-detail > div > p:nth-child(1) > span:nth-child(2)").get(0).text();
        Log.e(result.bookAuthor)
        results.add(result);
        Log.e(bookEl.tagName()+"<<<<<"+i+"<<<<<<<<<<<<<<<<<<<<")
    }
    return results;
}

function getDetails(url){
    var parse = Jsoup.parse(http.get(url).body,url);
    var book = new Book();
    //书籍信息
    book.url = url;
    book.title = parse.select("meta[property=\"og:title\"]").attr("content");
    book.author = parse.select("meta[property=\"og:novel:author\"]").attr("content");
    book.intro = parse.select("meta[property=\"og:description\"]").attr("content");
//    book.cover = parse.select(".pic > img").get(0).absUrl("src");
    //加载章节列表
    var children = parse.select("#list a");
    var chapterList = new ArrayList();
    for(i=0; i<children.size(); i++){
        var chapterEl = children.get(i);
        var chapter = new Chapter();
        chapter.title = chapterEl.text();
        chapter.url = chapterEl.absUrl("href");
        chapterList.add(chapter);
    }
    book.chapterList = chapterList;
    return book;
}

function getChapterContent(url){
    var html = http.get(url).body;
    return Jsoup.parse(html).selectFirst("#content").outerHtml().split("网页版章节内容慢，请下载爱阅小说app阅读最新内容")[0];
}
