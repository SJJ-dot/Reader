function search(query){
    var baseUrl = "https://www.bing.com";
    var html = http.get(baseUrl+"/search?q=" + URLEncoder.encode("site:ddxs.com "+query, "utf-8")).body;
    var parse = Jsoup.parse(html,baseUrl);

    var bookListEl = parse.select("#b_results .b_algo .b_caption cite");

    Log.e(">>>>>bookListEl Size "+bookListEl.size()+">>>>>>>>>>>>>>>>>>>>")
    var results = new ArrayList();
    for (var i=0;i<bookListEl.size();i++){
        var bookEl = bookListEl.get(i);
        Log.e(bookEl.tagName()+">>>>>"+i+">>>>>>>>>>>>>>>>>>>>")
        Log.e("bookEl>>>>")
        Log.e(bookEl.text())
        var url = bookEl.text().match(/^(https?:\/\/[a-zA-Z0-9]+\.ddxs.com\/[a-zA-Z0-9]+)\/?/)[1].replace("//m.","//www.")
        Log.e("url>>>>"+url)
        try{
            var parseBook = Jsoup.parse(http.get(url).body,url);
            var result = new SearchResult();
            Log.e("bookTitle>>>>")
            result.bookTitle = parseBook.select(".btitle h1").get(0).text();
            Log.e(result.bookTitle)
            Log.e("bookUrl>>>>")
            result.bookUrl = parseBook.select("meta[name=\"og:novel:read_url\"]").attr("content");
            Log.e(result.bookUrl)
            Log.e("bookAuthor>>>>")
            result.bookAuthor = parseBook.select(".btitle i").get(0).text().replace("作者：","");
            Log.e(result.bookAuthor)
            results.add(result);
        }catch(error){
            Log.e("书籍内容解析错误"+error)
            break;
        }
    }
    return results;
}

function getDetails(url){
    var parse = Jsoup.parse(http.get(url).body,url);
    var book = new Book();
    //书籍信息
    book.url = url;
    book.title = parse.select(".btitle h1").get(0).text();
    book.author = parse.select(".btitle i").get(0).text().replace("作者：","");
    book.intro = parse.select(".intro").html();
    book.cover = parse.select(".pic > img").get(0).absUrl("src");
    //加载章节列表
    var children = parse.select("tbody").get(1).select("a");
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
    var parse = Jsoup.parse(http.get(url).body,url);
    Log.e("章节内容>>>")
    var content = parse.select("#contents").html();
    Log.e(content)
    return content;
}
