import json
from urllib.parse import urljoin, quote

from bs4 import BeautifulSoup
from log import log

from SessionManager import verification_activity_get
from WebViewClient import web_get


# 支持搜索可以不需要实现verify
# def verify(query):
#     res = getDetails("https://www.min-yuan.com/txt/wk/")
#     res = getChapterContent(res["chapterList"][0]["url"])
#     return 0 if len(res) > 10 else 1


def getSiteUrl():
    return "https://www.min-yuan.com"


def _fetch(url):
    """web_get 优先（WebView 渲染，自动过 JS 挑战），失败则回退到 verification_activity_get（人机验证）"""
    def parse(result):
        result = json.loads(result)
        html = result.get("html", "")
        if "https://challenges.cloudflare.com" in html:
            raise Exception("人机验证")
        if "此验证用于防止机器人自动化访问" in html:
            raise Exception("人机验证")
        return html

    try:
        return parse(web_get(url))
    except Exception as e:
        log(f"Error: {e}")
        return parse(verification_activity_get(url))


def search(query):
    url = urljoin(getSiteUrl(), f"/search/?searchkey={quote(query)}")
    html = _fetch(url)
    soup = BeautifulSoup(html, 'html.parser')
    books = []

    for i, bookEl in enumerate(soup.select(".item")):
        books.append({
            "bookTitle": bookEl.select("a")[1].text,
            "bookUrl": urljoin(getSiteUrl(), bookEl.select("a")[1].attrs["href"]),
        })
    return books


def getDetails(url):
    html = _fetch(url)
    soup = BeautifulSoup(html, 'html.parser')
    book = {
        "url": url,
        "title": soup.select("meta[property='og:novel:book_name']")[0].get("content"),
        "author": soup.select("meta[property='og:novel:author']")[0].get("content"),
        "intro": soup.select("meta[property='og:description']")[0].get("content"),
        "cover": urljoin(url, soup.select("meta[property='og:image']")[0].get("content")),
        "chapterList": []
    }
    chapter_elements = soup.select("#newlist a")
    for chapter_element in chapter_elements:
        book["chapterList"].append({
            "title": chapter_element.text.strip(),
            "url": urljoin(url, chapter_element.get("href"))
        })

    return book


def getChapterContent(url):
    html = _fetch(url)
    soup = BeautifulSoup(html, 'html.parser')
    con = soup.select_one("#booktxt")
    content = con.prettify()
    next_url = urljoin(url, soup.select("a[rel='next']")[0].get("href"))
    if "_" in next_url:
        content += getChapterContent(next_url)

    return content


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
