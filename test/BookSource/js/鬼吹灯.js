function search(query){
    var baseUrl = "http://www.cxbz958.la/";
    var html = http.get(baseUrl + "s.php?ie=utf-8&q=" + URLEncoder.encode(query, "utf-8")).body;
    var parse = Jsoup.parse(html, baseUrl);
    var bookListEl = parse.select(".bookbox");
    var results = new ArrayList();
    for (var i=0;i<bookListEl.size();i++){
        var bookEl = bookListEl.get(i);
        var result = new SearchResult();
        result.bookTitle = bookEl.select("div.bookinfo > h4 > a").text();
        result.bookUrl = bookEl.select("div.bookinfo > h4 > a").get(0).absUrl("href");
        result.bookAuthor = bookEl.select("div.bookinfo > div.author").text().replace("作者：", "");
        result.bookCover = bookEl.select("div.bookimg > a > img").get(0).absUrl("src");
        result.latestChapter = bookEl.select("div.bookinfo > div.update > a").get(0).text();
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
    book.url = url;
    book.title = parse.select("body > div.book > div.info > h2").get(0).text();
    book.author = parse.select("body > div.book > div.info > div.small > span:nth-child(1)").text().replace("作者：", "");
    book.intro = parse.select("body > div.book > div.info > div.intro").html();
    book.cover = parse.select("body > div.book > div.info > div.cover > img").get(0).absUrl("src");
    //加载章节列表
    var chapterList = new ArrayList();
//    var chapterListUrl = parse.select("#newlist > div > strong > a").get(0).absUrl("href");
//    var chapterListHtml = Jsoup.parse(http.get(chapterListUrl).body, chapterListUrl);
    var chapterListEl = parse.select("body > div.listmain > dl > *");
    for(i=chapterListEl.size() - 1; i>= 0;i--){
        if (chapterListEl.get(i).tagName().equals("dt")) {
            break;
        }
        var chapterEl = chapterListEl.get(i).child(0);
        var chapter = new Chapter();
        chapter.title = chapterEl.text();
        chapter.url = chapterEl.absUrl("href");
        chapterList.add(0,chapter);
    }

    book.chapterList = chapterList;
    return book;
}

function getChapterContent(url){
    var html = http.get(url).body;
    return Jsoup.parse(html).select("#content").outerHtml();
}