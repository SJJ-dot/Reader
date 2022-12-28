

function search(query){
    var baseUrl = "http://www.soduzw.com/search.html";
    var map = new HashMap();
    map.put("searchtype", "novelname")
    map.put("searchkey",URLEncoder.encode(query,"utf-8"))
    var html = http.post(baseUrl,map).body;
    var parse = Jsoup.parse(html, baseUrl);
    var bookListEl = parse.select(".Search");
    Log.e(">>>>>bookListEl Size "+bookListEl.size()+">>>>>>>>>>>>>>>>>>>>")
    var results = new ArrayList();
    for (var i=0;i<bookListEl.size();i++){
        var bookEl = bookListEl.get(i);
        Log.e(bookEl.tagName()+">>>>>"+i+">>>>>>>>>>>>>>>>>>>>")
        Log.e("bookEl>>>>")
        Log.e(bookEl)
        var result = new SearchResult();
        Log.e("bookTitle>>>>")
        result.bookTitle = bookEl.selectFirst("a").text();
        Log.e(result.bookTitle)
        Log.e("bookUrl>>>>")
        result.bookUrl = bookEl.selectFirst("a").absUrl("href").replace("mulu_","").replace(".html","/");
        Log.e(result.bookUrl)
        Log.e("bookAuthor>>>>")
        result.bookAuthor = bookEl.select("span").get(1).text().replace("作者：","");
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
    book.title = parse.select(".Mulu_title").get(0).ownText();
    book.author = parse.select(".Look a").get(0).text();
    book.intro = parse.select(".Look > div > div").get(0).html();
//    book.cover = parse.select(".pic > img").get(0).absUrl("src");
    //加载章节列表
    var children = parse.select(".Look_list_dir .chapter a");
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
    Log.e("加载章节内容 ："+url)
    var map = new HashMap();
    map.put("bid",url.match(/mulu_([a-zA-Z0-9]+)\/([a-zA-Z0-9]+).html/)[1])
    map.put("cid",url.match(/mulu_([a-zA-Z0-9]+)\/([a-zA-Z0-9]+).html/)[2])
    map.put("siteid","0")
    map.put("url","")
    var resp = http.post( "http://www.soduzw.com/novelsearch/chapter/transcode.html", map)
    var content = eval (" (" + resp.body + ")")["info"]
    Log.e("content>>>>>>")
    Log.e(content)
    return content;
}
