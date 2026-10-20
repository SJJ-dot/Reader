import json
from urllib.parse import urljoin, quote

from bs4 import BeautifulSoup

from SessionManager import verification_activity_get
from WebViewClient import web_get
from log import log

# 站点搜索接口有访问验证，直接走 WebView
_VERIFY_MARKERS = (
    "challenges.cloudflare.com",
    "此验证用于防止机器人自动化访问",
    "Just a moment",
)


def _is_verify(html):
    return any(marker in html for marker in _VERIFY_MARKERS)


def _fetch(url):
    """直接走 WebView；后台 WebView 被拦截时转前台人工验证，返回 {url, html} 的 JSON"""
    def parse(result):
        result = json.loads(result)
        result["html"] = result.get("html", "")
        if _is_verify(result["html"]):
            raise Exception("人机验证")
        return result

    try:
        return json.dumps(parse(web_get(url)), ensure_ascii=False)
    except Exception as e:
        log(f"web_get 失败，转人工验证: {e}")
        return json.dumps(parse(verification_activity_get(url)), ensure_ascii=False)


def getSiteUrl():
    return "https://m.terry-haass.com"


def search(query):
    def parseResult(result):
        result = json.loads(result)
        log("url:" + result.get("url"))
        log("html:" + result.get("html"))
        if "此验证用于防止机器人自动化访问，保护网站安全" in result.get("html"):
            raise Exception("人机验证")
        if "https://challenges.cloudflare.com" in result.get("html"):
            raise Exception("人机验证")
        soup = BeautifulSoup(result.get("html"), 'html.parser')
        books = []

        for i, bookEl in enumerate(soup.select(".sone")):
            bookUrl = bookEl.select("a")[0].get("href")
            bookUrl = f"https://m.terry-haass.com/{bookUrl.split('/')[1]}/"
            books.append({
                "bookTitle": bookEl.select("a")[0].text,
                "bookUrl": urljoin(url, bookUrl),
                "bookAuthor": bookEl.select("a")[1].text,
            })

        return books

    url = f"https://m.terry-haass.com/modules/article/search.php?searchkey={quote(query)}&Submit.x=20&Submit.y=16"
    return parseResult(_fetch(url))


def getDetails(url):
    def parseBookInfo(result):
        result = json.loads(result)
        log("url:" + result.get("url"))
        log("html:" + result.get("html"))

        if "此验证用于防止机器人自动化访问，保护网站安全" in result.get("html"):
            raise Exception("人机验证")
        if "https://challenges.cloudflare.com" in result.get("html"):
            raise Exception("人机验证")

        soup = BeautifulSoup(result.get("html"), 'html.parser')
        book = {
            "url": url,
            "title": soup.select("meta[property='og:novel:book_name']")[0].get("content"),
            "author": soup.select("meta[property='og:novel:author']")[0].get("content"),
            "intro": soup.select("meta[property='og:description']")[0].get("content"),
            "cover": soup.select("meta[property='og:image']")[0].get("content"),
        }
        return book

    def parseChapterList(result):
        result = json.loads(result)
        log("url:" + result.get("url"))
        log("html:" + result.get("html"))

        if "此验证用于防止机器人自动化访问，保护网站安全" in result.get("html"):
            raise Exception("人机验证")
        if "https://challenges.cloudflare.com" in result.get("html"):
            raise Exception("人机验证")

        soup = BeautifulSoup(result.get("html"), 'html.parser')
        children = soup.select(".infoad")
        chapterList = []
        for chapter_el in children:
            chapter = {
                "title": chapter_el.text.split("&nbsp;")[0].strip(),
                "url": urljoin(url, chapter_el.get("href"))
            }
            chapterList.append(chapter)
        return chapterList

    url = url.replace("://www.", "://m.")
    book = parseBookInfo(_fetch(url))
    book["chapterList"] = parseChapterList(_fetch(url + "/index.html"))

    return book


def getChapterContent(url):
    def parseContent(result):
        result = json.loads(result)
        log("url:" + result.get("url"))
        log("html:" + result.get("html"))

        if "此验证用于防止机器人自动化访问，保护网站安全" in result.get("html"):
            raise Exception("人机验证")
        if "https://challenges.cloudflare.com" in result.get("html"):
            raise Exception("人机验证")

        soup = BeautifulSoup(result.get("html"), 'html.parser')
        content = soup.select("#novelcontent > p")[0:-2]
        content = "<p>".join([p.text.strip() for p in content])
        nextspan = soup.select(".nextspan")[0]
        if nextspan is not None and nextspan.text == "下一页":
            nexturl = urljoin(url, soup.select(".chapternext a")[0].get("href"))
            content = content + getChapterContent(nexturl)
        return content

    return parseContent(_fetch(url))


if __name__ == '__main__':
    res = search("我的")
    print(res)

    print("================================")
    print("================================")
    print("================================")
    res = getDetails(res[0]["bookUrl"])
    print(res)

    print("================================")
    print("================================")
    print("================================")
    res = getChapterContent(res["chapterList"][0]["url"])
    print(res)
