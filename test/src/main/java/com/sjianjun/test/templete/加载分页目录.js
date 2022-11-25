
function search(query){
    var baseUrl = "http://www.linshuwu.com/";
    var html = http.get(baseUrl+"search/?searchkey=" + URLEncoder.encode(query, "utf-8")).body;
    var parse = Jsoup.parse(html, baseUrl);
    //多个class 不能加空格
    var bookListEl = parse.select(".col-xs-4.book-coverlist");
    Log.e(">>>>>bookListEl Size "+bookListEl.size()+">>>>>>>>>>>>>>>>>>>>")
    var results = new ArrayList();
    for (var i=0;i<bookListEl.size();i++){
        var bookEl = bookListEl.get(i);
        Log.e(bookEl.tagName()+">>>>>"+i+">>>>>>>>>>>>>>>>>>>>")
        Log.e("bookEl>>>>")
        Log.e(bookEl)
        var result = new SearchResult();
        Log.e("bookTitle>>>>")
        result.bookTitle = bookEl.select(".fs-16.text-muted > a").text();
        Log.e(result.bookTitle)
        Log.e("bookUrl>>>>")
        result.bookUrl = bookEl.selectFirst(".fs-16.text-muted > a").absUrl("href");
        Log.e(result.bookUrl)
        Log.e("bookAuthor>>>>")
        result.bookAuthor = bookEl.selectFirst(".fs-14.text-muted").text();
        Log.e(result.bookAuthor)
        results.add(result);
        Log.e(bookEl.tagName()+"<<<<<"+i+"<<<<<<<<<<<<<<<<<<<<")
    }
    return results;
}


function getDetails(url){
    var parse = Jsoup.parse(http.get(url).body,url);
    var book = new Book();
    book.url = url;
    Log.e("title>>>>")
    book.title = parse.selectFirst("h1.bookTitle").text().trim();
    Log.e(book.title)
    Log.e("author>>>>")
    book.author = parse.selectFirst("p.booktag a.red").text();
    Log.e(book.author)
    Log.e("intro>>>>")
    //选择没有子元素的P元素
    book.intro = parse.selectFirst(".col-sm-10.pl0 > p:empty").outerHtml();
    Log.e(book.intro)
    Log.e("cover>>>>")
    book.cover = parse.selectFirst(".img-thumbnail").absUrl("src");
    Log.e(book.cover)
    //加载章节列表
    var chapterUrl = parse.selectFirst(".bookmore .btn.btn-info").absUrl("href");
    var chapterList = new ArrayList();
    getChapterList(http,chapterUrl,chapterList)
    book.chapterList = chapterList;
    return book;
}

function getChapterList(http,url,chapterList){
    var parse = Jsoup.parse(http.get(url).body,url);
    var chapterListEl = parse.select(".col-sm-3 a");
    for (var i=0;i<chapterListEl.size();i++){
        var chapterEl = chapterListEl.get(i);
        var chapter = new Chapter();
        chapter.title = chapterEl.text();
        chapter.url = chapterEl.absUrl("href");
        chapterList.add(chapter);
    }
    var a = parse.selectFirst(".index-container-btn");
    if(a.text().equals("下一页")){
        getChapterList(http,a.absUrl("href"),chapterList)
    }
}

function getChapterContent(url){
    var html = http.get(url).body;
    return Jsoup.parse(html).selectFirst("#booktxt").outerHtml();
}