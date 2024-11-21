import requests
from bs4 import BeautifulSoup
from urllib.parse import urljoin, quote

from log import log


def search(query):
    url = "https://www.mayiwsk.com/modules/article/search.php"
    data = {
        "searchtype": "articlename",
        "searchkey": query
    }
    response = requests.post(url, data=data, timeout=(5, 10))
    response.encoding = 'utf-8'
    log(response.request)
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    book_list_el = soup.select("#nr")
    results = []

    for book_el in book_list_el:
        result = {
            "bookTitle": book_el.select_one("td:nth-child(1) > a").text,
            "bookUrl": urljoin(url, book_el.select_one("td:nth-child(1) > a").get("href")),
            "bookAuthor": book_el.select_one("td:nth-child(3)").text
        }
        results.append(result)

    return results


def getDetails(url):
    response = requests.get(url, timeout=(5, 10))
    response.encoding = 'utf-8'
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    book = {
        "url": url,
        "title": soup.select_one("[property='og:novel:book_name']").get("content"),
        "author": soup.select_one("[property='og:novel:author']").get("content"),
        "intro": soup.select_one("#intro").prettify(),
        "cover": soup.select_one("#fmimg > img").get("src"),
        "chapterList": []
    }

    for chapter_el in reversed(soup.select("#list dl > *")):
        if chapter_el.name == "dt":
            break
        chapter = {
            "title": chapter_el.select_one("a").text,
            "url": urljoin(url, chapter_el.select_one("a").get("href"))
        }
        book["chapterList"].insert(0, chapter)

    return book


def getChapterContent(url):
    response = requests.get(url, timeout=(5, 10))
    response.encoding = 'utf-8'
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    content = soup.select_one("#content").prettify()
    return content


