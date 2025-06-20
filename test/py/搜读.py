import requests
from bs4 import BeautifulSoup
from urllib.parse import urlencode, urljoin

from log import log


def search(query):
    base_url = "http://www.soduzw.com/search.html"
    data = {
        "searchtype": "novelname",
        "searchkey": query
    }
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Content-Type": "application/x-www-form-urlencoded",
    }
    response = requests.post(base_url, data=data, headers=headers, timeout=(5, 10))
    response.encoding = 'utf-8'
    # log(response.request)
    # log(response.text)
    soup = BeautifulSoup(response.text, 'html.parser')
    book_list_el = soup.select(".Search")
    results = []

    for book_el in book_list_el:
        result = {
            "bookTitle": book_el.select_one("a").text,
            "bookUrl": urljoin(base_url, book_el.select_one("a")["href"].replace("mulu_", "").replace(".html", "/")),
            "bookAuthor": book_el.select("span")[1].text.replace("作者：", "")
        }
        results.append(result)

    return results


def getDetails(url):
    response = requests.get(url, timeout=(5, 10))
    response.encoding = 'utf-8'
    # log(response.request)
    # log(response.text)
    soup = BeautifulSoup(response.text, 'html.parser')
    book = {
        "url": url,
        "title": soup.select("meta[property='og:novel:book_name']")[0].get("content"),
        "author": soup.select("meta[property='og:novel:author']")[0].get("content"),
        "intro": soup.select("meta[property='og:description']")[0].get("content"),
        "chapterList": []
    }

    children = soup.select(".Look_list_dir .chapter a")
    for chapter_el in children:
        chapter = {
            "title": chapter_el.text,
            "url": urljoin(url, chapter_el["href"])
        }
        book["chapterList"].append(chapter)

    return book


def getChapterContent(url):
    bid = url.split("mulu_")[1].split("/")[0]
    cid = url.split("mulu_")[1].split("/")[1].replace(".html", "")
    data = {
        "bid": bid,
        "cid": cid,
        "siteid": "0",
        "url": ""
    }
    response = requests.post("http://www.soduzw.com/novelsearch/chapter/transcode.html", data=data, timeout=(5, 10))
    # log(response.request)
    # log(response.text)
    content = response.json()["info"]
    return content


