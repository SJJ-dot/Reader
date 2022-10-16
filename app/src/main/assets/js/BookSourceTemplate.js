/*
可用方法：
encode(s,enc)
decode(s,enc)
get({url:str,data:{},header:{},enc:str}) -> doc
post({url:str,data:{},header:{},enc:str}) -> doc

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

//书源地址
var sourceUrl = "https://www.qidian.com"
function search(query){
    var doc = get({url:"https://www.qidian.com/soushu/" + encode(query, "utf-8")+".html"})
    var bookListEl = doc.select(".res-book-item");
    var results = new ArrayList();
    for (var i=0;i<bookListEl.size();i++){
        var bookEl = bookListEl.get(i);
        var result = new SearchResult();
        result.bookTitle = bookEl.selectFirst(".book-title").text();
        result.bookUrl = bookEl.absUrl("href")+".html";
        result.bookAuthor = bookEl.selectFirst(".book-author").ownText();
        results.add(result);
    }
    return results
}