function search(query){
    var baseUrl = "https://m.xquledu.com/search/";
    var map = new HashMap(); 
    
    map.put("searchtype","all")
    map.put("searchkey",URLEncoder.encode(query,"gbk"))
    map.put("act","search")
    var html = http.post(baseUrl,map).body;
    var parse = Jsoup.parse(html,baseUrl);

    var bookListEl = parse.select("dl");
    Log.e(">>>>>bookListEl Size "+bookListEl.size()+">>>>>>>>>>>>>>>>>>>>")
    var results = new ArrayList();
    for (var i=0;i<bookListEl.size();i++){
        var bookEl = bookListEl.get(i);
        Log.e(bookEl.tagName()+">>>>>"+i+">>>>>>>>>>>>>>>>>>>>")
        Log.e("bookEl>>>>")
        Log.e(bookEl)
        var result = new SearchResult();
        result.bookTitle = bookEl.select("dt a").text();
        result.bookUrl = bookEl.select("dt a").get(0).absUrl("href").replace("//m.","//www.");
        result.bookAuthor = bookEl.select("dd a").get(0).text();
        results.add(result);
    }
    return results;
}

function getDetails(url){
    var parse = Jsoup.parse(http.get(url).body,url);
    var book = new Book();
    //书籍信息
    book.url = url;
    book.title = parse.select(".bookinfo .booktitle a").get(0).text();
    book.author = parse.select(".bookinfo .booktitle a").get(1).text();
    book.intro = parse.select(".bookintro").html();
    book.cover = parse.select(".book img").get(0).absUrl("src");
    //加载章节列表
    var children = parse.select(".all ul a");
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
    var parse = Jsoup.parse(http.get(url).body,url);
    var content = parse.select(".content").html();
    return content;
}
