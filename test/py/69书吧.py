import json
from urllib.parse import urljoin

from bs4 import BeautifulSoup

from WebViewClient import web_get
from SessionManager import verification_activity_get
from log import log

def verify():
    try:
        res = getDetails("https://www.69shuba.com/book/90442.htm")
        res = getChapterContent(res["chapterList"][0]["url"])
        return len(res) > 10
    except Exception as e:
        log(f"Error :{e}")
        return False

def getSiteUrl():
    return "https://www.69shuba.com"

def search(query):
    # 不支持搜索，太麻烦了
    return []


def getDetails(url):
    def parseBookInfo(result):
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
        return book

    try:
        book = parseBookInfo(web_get(url))
    except Exception as e:
        log(f"Error :{e}")
        book = parseBookInfo(verification_activity_get(url))
    get_chapter_list(url.replace(".htm", "/"), book["chapterList"])

    return book


def get_chapter_list(url, chapter_list):
    def parseChapterInfo(result):
        result = json.loads(result)
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
    try:
        parseChapterInfo(web_get(url))
    except Exception as e:
        log(f"Error :{e}")
        parseChapterInfo(verification_activity_get(url))


def getChapterContent(url):
    def parseChapterContent(result):
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
    try:
        return parseChapterContent(web_get(url))
    except Exception as e:
        log(f"Error :{e}")
        return parseChapterContent(verification_activity_get(url))
