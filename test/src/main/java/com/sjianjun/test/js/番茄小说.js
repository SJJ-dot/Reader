var hostUrl = "http://www.fanqianxs.com/";
function search(query){
    var baseUrl = hostUrl;
    var map = new HashMap();
    map.put("keyword",URLEncoder.encode(query,"utf-8"))
    var html = http.post(baseUrl+"modules/article/search.php",map).body;
    var parse = Jsoup.parse(html,baseUrl);

    var bookList = parse.select(".novelslist2").select("li");
    var results = new ArrayList();
    for (var i=1;i<bookList.size();i++){
        var bookElement = bookList.get(i);
        var result = new SearchResult();
        result.source = source;
        result.bookTitle = bookElement.select(".s2 a").text();
        result.bookUrl = bookElement.select(".s2 a").get(0).absUrl("href");
        result.bookAuthor = bookElement.select(".s4").text();
        //result.bookCover = bookElement.getElementsByClass("c").get(0).getElementsByTag("img").get(0).absUrl("src");
        //result.latestChapter = bookElement.select(".s3 a").text();
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
    //书籍信息
    var bookInfo = parse.select(".box_con").get(0);
    book.url = url;
    book.title = bookInfo.select("#maininfo #info h1").text();
    book.author = bookInfo.select("#maininfo #info p").get(0).text().replace("作 者：","");
    book.intro = bookInfo.select("#intro").html();
    book.cover = bookInfo.select("#fmimg img").get(0).absUrl("src");
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
