/*
可用方法：
encode(s,enc)
decode(s,enc)
get({baseUrl:str,url:str,data:{},header:{},enc:str}) -> doc
post({baseUrl:str,url:str,data:{},header:{},enc:str}) -> doc

doc el 常用方法
select(cssQuery) -> list[el]
selectFirst(cssQuery) -> el
child(n)
children()
text()  <p>Hello <b>there</b> now! </p>, p.text() returns "Hello there now!"
ownText() <p>Hello <b>there</b> now!</p>, p.ownText() returns "Hello now!"
outerHtml() <div><p></p></div> return <div><p></p></div>
html() <div><p>Para</p></div> return <p>Para</p>
absUrl("href")
absUrl("src")
java常用方法
str.replace("著","")
str.trim()
*/

//请求延迟的时间ms，如果时长小于0将会并发
function search(query){
//    var baseUrl = "https://m.qidian.com/";
//    var html = http.get(baseUrl + "soushu/" + URLEncoder.encode(query, "utf-8"))+".html";
//    var parse = Jsoup.parse(html,baseUrl);
    var parse = get({url:"https://www.qidian.com/soushu/" + encode(query, "utf-8")+".html"})
    var bookListEl = parse.select(".book-layout");
    var results = new ArrayList();
    for (var i=0;i<bookListEl.size();i++){
        var bookEl = bookListEl.get(i);

        var result = new SearchResult();
        result.source = source;
        result.bookTitle = bookEl.selectFirst(".book-title").text();
        result.bookUrl = bookEl.absUrl("href")+".html";
        result.bookAuthor = bookEl.selectFirst(".book-author").ownText();
        results.add(result);
    }
    return results;
}

/**
 * 书籍详情[JavaScript.source]
 */
function getDetails(url){
    if(url.indexOf("?")!=-1){
        url = url + "&_csrfToken="+CookieMgr.getCookie(url,"_csrfToken")
    }else{
        url = url + "?_csrfToken="+CookieMgr.getCookie(url,"_csrfToken")
    }

    var parse = Jsoup.parse(http.get(url),url);
    var book = new Book();
    book.source = source;

    book.url = url;
    book.title = parse.selectFirst(".book-summary-bookname").text();
    book.author = parse.selectFirst(".book-summary-author").text().replace("著","").trim();
    book.intro = parse.selectFirst(".book-intro-info").ownText();
    book.cover = parse.selectFirst(".book-name-img").absUrl("src");
    //加载章节列表
    var bookId = "";
    var elements = parse.select("script");
    for (i = 0; i < elements.size(); i++) {
        var data = elements.get(i).data();
        if (data.indexOf("g_data.book")!=-1) {
            try{
                context.eval(data);
                bookId = g_data.book["bookId"]
                break;
            }catch(error){
                Log.e(source+"解析章节列表出错，"+error)
                break;
            }
        }
    }
    var chapterList = new ArrayList();
    //var chapterUrl = "https://m.qidian.com/majax/book/category"+"?_csrfToken="+CookieMgr.getCookie(url,"_csrfToken")+"&bookId="+bookId;
    var chapterListUrl = "https://m.qidian.com/book/" + bookId + "/catalog";
    var chapterListHtml = http.get(chapterListUrl);
    var chapterListParse =  Jsoup.parse(chapterListHtml,chapterListUrl);
    var elements = chapterListParse.select("script");
    for (i = 0; i < elements.size(); i++) {
        var data = elements.get(i).data();
        if (data.contains("g_data.volumes")) {
            try{
                context.eval(data);
                for (i = 0; i < g_data.volumes.length; i++) {
                    var chapterListJson = g_data.volumes[i]["cs"]
                    for (j = 0; j < chapterListJson.length; j++) {
//                        https://m.qidian.com/book/1018313916/516635756
                        var chapterJson = chapterListJson[j];
                        var chapter = new Chapter();
                        chapter.bookUrl = book.url;
                        chapter.title = chapterJson["cN"];
                        chapter.url = "https://m.qidian.com/book/"+bookId+"/"+chapterJson["id"];
                        chapterList.add(chapter);
                    }
                }
                break;
            }catch(error){
                Log.e(source+"解析章节列表出错，"+error)
                break;
            }
        }
    }
    book.chapterList = chapterList;
    return book;
}

function getChapterContent(url){
    var html = http.get(url);
    if (url.startsWith("https://m.qidian.com/")) {
        return Jsoup.parse(html).select(".jsChapterWrapper > div").outerHtml();
    } else {
        return Jsoup.parse(html).select(".main-text-wrap  div.read-content").html();
    }
}