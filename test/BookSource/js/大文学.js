//请求延迟的时间ms，如果时长小于0将会并发
function search(query){
    var baseUrl = "https://m.wcxsw.org/search.php";
    var map = new HashMap();
    map.put("keyword",URLEncoder.encode(query,"utf-8"))
    map.put("submit",URLEncoder.encode("搜索","utf-8"))
    var html = http.post(baseUrl,map).body;
    var parse = Jsoup.parse(html,baseUrl);

    var bookList = parse.select("dl");
    var results = new ArrayList();
    for (var i=0;i<bookList.size();i++){
        var bookElement = bookList.get(i);
        var result = new SearchResult();
        result.bookTitle = bookElement.select("font").text();
        result.bookUrl = bookElement.select("a").get(0).absUrl("href").replace("//m.","//www.");
        result.bookAuthor = bookElement.select("dd p").get(0).text().replace("作者：","");
        results.add(result);
    }
    return results;
}

function getDetails(url){
    var parse = Jsoup.parse(http.get(url).body,url);
    var book = new Book();
    //书籍信息
    book.url = url;
    book.title = parse.select("#maininfo #info h1").text();
    book.author = parse.select("#maininfo #info p").text().replace("作 者：","");
    book.intro = parse.select("#intro").html();
    book.cover = parse.select("#fmimg img").get(0).absUrl("src");
    //加载章节列表
    var children = parse.select("#list dl").get(0).children();
    var chapterList = new ArrayList();
    for(i=children.size()-1; i>=0; i--){
        var chapterEl = children.get(i);
        if(chapterEl.tagName() == "dt"){
            break;
        }
        var chapter = new Chapter();
        chapter.title = chapterEl.child(0).text();
        chapter.url = chapterEl.child(0).absUrl("href");
        chapterList.add(0,chapter);
    }
    book.chapterList = chapterList;
    return book;
}

function getChapterContent(url){
    var parse = Jsoup.parse(http.get(url).body,url);
    var content = parse.getElementById("content").html();
    return content;
}
