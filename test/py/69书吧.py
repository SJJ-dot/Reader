import json
from urllib.parse import urljoin

from bs4 import BeautifulSoup

from WebViewClient import web_get
from log import log


def isSupported(url):
    if "69shuba.com" in url:
        return True
    return False


def search(query):
    # 不支持搜索，太麻烦了
    return []


def getDetails(url):
    result = web_get(url)
    if not result:
        raise "No result returned from web_get"

    result = json.loads(result)
    log("url:" + result.get("url"))
    log("html:" + result.get("html"))
    soup = BeautifulSoup(result["html"], 'html.parser')
    book = {
        "url": result.get("url"),
        "title": soup.select("meta[property='og:novel:book_name']")[0].get("content"),
        "author": soup.select("meta[property='og:novel:author']")[0].get("content"),
        "intro": soup.select("meta[property='og:description']")[0].get("content"),
        "cover": soup.select("meta[property='og:image']")[0].get("content"),
        "chapterList": []
    }
    get_chapter_list(url.replace(".htm", "/"), book["chapterList"])

    return book


def get_chapter_list(url, chapter_list):
    response = web_get(url)
    if not response:
        log("No result returned from web_get")
        return
    result = json.loads(response)
    log("url:" + result.get("url"))
    log("html:" + result.get("html"))
    soup = BeautifulSoup(result.get("html"), 'html.parser')
    chapter_list_el = soup.select("#catalog a")
    for i, chapter_el in enumerate(chapter_list_el):
        if i == 0:
            continue
        chapter = {
            "title": chapter_el.text.strip(),
            "url": urljoin(result.get("url"), chapter_el.get("href"))
        }
        chapter_list.append(chapter)


def getChapterContent(url):
    result = web_get(url)
    if not result:
        raise "No result returned from web_get"

    result = json.loads(result)
    log("url:" + result.get("url"))
    log("html:" + result.get("html"))
    soup = BeautifulSoup(result["html"], 'html.parser')
    txt_el = soup.select_one(".txtnav")
    # 删除h1
    for h1 in txt_el.find_all("h1"):
        h1.decompose()
    for h1 in txt_el.find_all("div"):
        h1.decompose()

    return txt_el.prettify()


if __name__ == '__main__':
    log("===============================")
    details = getDetails("")
    log("===============================")
    log(details)
    log("===============================")
    content = getChapterContent(details["chapterList"][0]["url"])
    log("===============================")
    log(content)