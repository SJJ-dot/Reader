function search(query){
    var baseUrl = "http://www.b5200.net/";
    var url = "/modules/article/search.php?searchkey={{key}}".replace("{{key}}",query);
    var doc = get({baseUrl:baseUrl,url:url})

    var bookListEl = doc.select("table > tbody > tr");
     Log.e(">>>>>bookListEl Size "+bookListEl.size()+">>>>>>>>>>>>>>>>>>>>")
    var results = new ArrayList();
    for (var i=1;i<bookListEl.size();i++){
        var bookEl = bookListEl.get(i);
        Log.e(bookEl.tagName()+">>>>>"+i+">>>>>>>>>>>>>>>>>>>>")
        Log.e("bookEl>>>>")
        Log.e(bookEl)
        var result = new SearchResult();
        result.source = source;
        Log.e("bookTitle>>>>")
        result.bookTitle = bookEl.select("td a").get(0).text();
        Log.e(result.bookTitle)
        Log.e("bookUrl>>>>")
        result.bookUrl = bookEl.select("td a").get(0).absUrl("href");
        Log.e(result.bookUrl)
        Log.e("bookAuthor>>>>")
        result.bookAuthor = bookEl.select("td").get(2).text();
        Log.e(result.bookAuthor)
        results.add(result);
        Log.e(bookEl.tagName()+"<<<<<"+i+"<<<<<<<<<<<<<<<<<<<<")
    }
    return results;
}

function getDetails(url){
    var doc = get({url:url});
    Log.e(url)
    var book = new Book();
    book.url = url;
    Log.e("title>>>>")
    book.title = doc.select("meta[property=\"og:novel:book_name\"]").attr("content");
    Log.e(book.title)
    Log.e("author>>>>")
    book.author = doc.select("meta[property=\"og:novel:author\"]").attr("content");
    Log.e(book.author)
    Log.e("intro>>>>")
    book.intro = doc.select("meta[property=\"og:description\"]").attr("content");
    Log.e(book.intro)
    Log.e("cover>>>>")
    book.cover = doc.select("#fmimg img").get(0).absUrl("src");
    Log.e(book.cover)
    var elements = doc.select("#list > dl > *");
    Log.e("章节列表数量："+elements.size())
    var chapterList = new ArrayList();
    var dt = false;
    for (i = 1; i < elements.size(); i++) {
        var el = elements.get(i);
        Log.e(el)
        if(!dt && el.tagName() == "dt"){
            dt = true;
            continue
        }
        if(!dt){
            continue
        }

        var chapter = new Chapter();
        Log.e("chapter.title>>>>")
        chapter.title = el.select("a").get(0).text()
        Log.e(chapter.title)
        Log.e("chapter.url>>>>")
        chapter.url = el.select("a").get(0).absUrl("href");
        Log.e(chapter.title)
        chapterList.add(chapter);
    }
    book.chapterList = chapterList;
    return book;
}

function getChapterContent(url){
    var doc = get({url:url});
    return doc.select("#content").html()
}