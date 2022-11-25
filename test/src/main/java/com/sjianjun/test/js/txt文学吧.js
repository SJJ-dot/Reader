var hostUrl = "http://www.txtjia8.com/";
function search(query){
    var baseUrl = hostUrl;
    var html = http.get(baseUrl+"search/?searchkey=" + URLEncoder.encode(query, "utf-8")).body;
    var parse = Jsoup.parse(html, baseUrl);
    var bookListEl = parse.select(".book-coverlist");
    var results = new ArrayList();
    for (var i=0;i<bookListEl.size();i++){
        var bookEl = bookListEl.get(i);
        var result = new SearchResult();
        result.source = source;
        result.bookTitle = bookEl.selectFirst(".book-coverlist .col-sm-7 .fs-16 a").text();
        result.bookUrl = bookEl.selectFirst(".book-coverlist .col-sm-7 .fs-16 a").absUrl("href");
        result.bookAuthor = bookEl.selectFirst(".book-coverlist .col-sm-7 .fs-14").text();
        results.add(result);
    }
    return results;
}

/**
 * 书籍详情[JavaScript.source]
 */
function getDetails(url){
    var parse = Jsoup.parse(http.get(url).body,url);
    var book = new Book();
    book.source = source;
    book.url = url;
    book.title = parse.selectFirst(".bookTitle").text().trim();
    book.author = parse.selectFirst(".booktag > a").text();
    book.intro = parse.select(".col-sm-10 > p:nth-child(n+3):nth-last-child(n+3)").outerHtml();
    book.cover = parse.selectFirst(".img-thumbnail").absUrl("src");
    //加载章节列表
    var chapterUrl = parse.selectFirst(".bookmore .btn-info").absUrl("href");
    var chapterList = new ArrayList();
    getChapterList(http,chapterUrl,chapterList);
    book.chapterList = chapterList;
    return book;
}
function getChapterList(http,url,chapterList){
    var parse = Jsoup.parse(http.get(url).body,url);
    var chapterListEl = parse.select(".panel-chapterlist a");
    for (var i=0;i<chapterListEl.size();i++){
        var chapterEl = chapterListEl.get(i);
        var chapter = new Chapter();
        chapter.title = chapterEl.text();
        chapter.url = chapterEl.absUrl("href");
        chapterList.add(chapter);
    }
    var a = parse.selectFirst(".listpage a:nth-child(3)");
    if(a.text().equals("下一页")){
        getChapterList(http,a.absUrl("href"),chapterList)
    }
}

function getChapterContent(url){
    var html = http.get(url).body;
    return Jsoup.parse(html).selectFirst("#booktxt").outerHtml();
}