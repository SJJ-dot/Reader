
function search(query){
    var baseUrl = "https://www.mayiwxw.com/";
    var map = new HashMap();
    map.put("searchtype", "articlename")
    map.put("searchkey",URLEncoder.encode(query,"utf-8"))
    var html = http.post(baseUrl+"modules/article/search.php",map).body;
    var parse = Jsoup.parse(html, baseUrl);
    var bookListEl = parse.select("#nr");
    Log.e(">>>>>bookListEl Size "+bookListEl.size()+">>>>>>>>>>>>>>>>>>>>")
    var results = new ArrayList();
    for (var i=0;i<bookListEl.size();i++){
        var bookEl = bookListEl.get(i);
        Log.e(bookEl.tagName()+">>>>>"+i+">>>>>>>>>>>>>>>>>>>>")
        Log.e("bookEl>>>>")
        Log.e(bookEl)
        var result = new SearchResult();
        Log.e("bookTitle>>>>")
        result.bookTitle = bookEl.selectFirst("> td:nth-child(1) > a").text();
        Log.e(result.bookTitle)
        Log.e("bookUrl>>>>")
        result.bookUrl = bookEl.selectFirst("> td:nth-child(1) > a").absUrl("href");
        Log.e(result.bookUrl)
        Log.e("bookAuthor>>>>")
        result.bookAuthor = bookEl.selectFirst("> td:nth-child(3)").text();
        Log.e(result.bookAuthor)
        results.add(result);
        Log.e(bookEl.tagName()+"<<<<<"+i+"<<<<<<<<<<<<<<<<<<<<")
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
    Log.e("title>>>>")
    book.title =  parse.selectFirst("[property=og:novel:book_name]").attr("content");
    Log.e(book.title)
    Log.e("author>>>>")
    book.author = parse.selectFirst("[property=og:novel:author]").attr("content");
    Log.e(book.author)
    Log.e("intro>>>>")
    book.intro = parse.selectFirst("#intro").outerHtml();
    Log.e(book.intro)
    Log.e("cover>>>>")
    book.cover = parse.selectFirst("#fmimg > img").absUrl("src");
    Log.e(book.cover)
    //加载章节列表
    var children = parse.selectFirst("#list dl").children();
    Log.e("章节列表数量："+children.size())
    var chapterList = new ArrayList();
    for(i=children.size()-1; i>=0; i--){
        var chapterEl = children.get(i);
        if(chapterEl.tagName() == "dt"){
            break;
        }
        var chapter = new Chapter();
        Log.e("chapter.title>>>>")
        chapter.title = chapterEl.child(0).text();
        Log.e(chapter.title)
        Log.e("chapter.url>>>>")
        chapter.url = chapterEl.child(0).absUrl("href");
        Log.e(chapter.title)
        chapterList.add(0,chapter);
    }
    book.chapterList = chapterList;
    return book;
}

function getChapterContent(url){
    var html = http.get(url).body;
    return Jsoup.parse(html).selectFirst("#content").outerHtml();
}