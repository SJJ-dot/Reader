//请求延迟的时间ms，如果时长小于0将会并发
var hostUrl = "http://www.changshengrui.com/";
function search(query){
    var baseUrl = hostUrl;
    var html = http.get(baseUrl+"search/?searchkey=" + URLEncoder.encode(query, "utf-8")).body;
    var parse = Jsoup.parse(html,baseUrl);
    var bookList = parse.select(".item");
    var results = new ArrayList();
    for (var i=0;i<bookList.size();i++){
        var bookElement = bookList.get(i);
        var result = new SearchResult();
        result.bookTitle = bookElement.selectFirst("dl > dt > a").text();
        result.bookUrl = bookElement.selectFirst("dl > dt > a").absUrl("href");
        result.bookAuthor = bookElement.selectFirst(".btm").ownText();
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
    //书籍信息
    book.url = url;
    book.title = parse.selectFirst("#info > h1").text();
    book.author = parse.selectFirst("#info > p:nth-child(2) > a").text();
    book.intro = parse.selectFirst("#intro").html();
    book.cover = parse.selectFirst("#fmimg > img").absUrl("src");
    var chapterUrl = parse.selectFirst("#maininfo .chapterlist").absUrl("href");
    var chapterList = new ArrayList();
    getChapterList(http,chapterUrl,chapterList)
    book.chapterList = chapterList;
    return book;
}

function getChapterList(http,url,chapterList){
    var parse = Jsoup.parse(http.get(url).body,url);
    var chapterListEl = parse.select("#list > dl > a");
    for (var i=0;i<chapterListEl.size();i++){
        var chapterEl = chapterListEl.get(i);
        var chapter = new Chapter();
        chapter.title = chapterEl.text();
        chapter.url = chapterEl.absUrl("href");
        chapterList.add(chapter);
    }
    var a = parse.selectFirst(".right a");
    if(a.text().equals("下一页")){
        getChapterList(http,a.absUrl("href"),chapterList)
    }
}

function getChapterContent(url){
    return getChapterContent0(url);
}

function getChapterContent0(url){
    var str = http.get(url).body
    var parse = Jsoup.parse(str,url);
    var content = parse.getElementById("booktxt").html();
    var a = parse.select(".next_url").get(0)
    if(a.text().equals("下一页")){
        return content+"\n"+getChapterContent0(str.match(/const\snext_page\s=\s'(.+)';/)[1])
    }
    return content;
}