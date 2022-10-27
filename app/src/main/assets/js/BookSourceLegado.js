rule = JSON.parse(rule)
Log.e(rule["bookSourceName"])
function search(query){
//    var baseUrl = "https://m.qidian.com/";
//    var html = http.get(baseUrl + "soushu/" + URLEncoder.encode(query, "utf-8"))+".html";
//    var parse = Jsoup.parse(html,baseUrl);
    var doc = get({baseUrl:rule["bookSourceUrl"],
    url:rule["searchUrl"].replace("{{key}}",query)})
//    搜索规则
    var ruleSearch = rule["ruleSearch"]

    var bookListEl = doc.select(".book-layout");
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