from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

from log import log


def getSiteUrl():
    return "http://www.xbiqugu.la"


def search(query):
    url = f"http://www.xbiqugu.la/modules/article/waps.php"
    headers = {
        "Content-Type": "application/x-www-form-urlencoded",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3"
    }
    response = requests.post(url, data={"searchkey": query}, headers=headers, timeout=(5, 10))
    response.encoding = 'utf-8'
    # log(response.request)
    # log(response.text)
    soup = BeautifulSoup(response.text, 'html.parser')
    book_list_el = soup.select(".grid tr")
    results = []

    for book_el in book_list_el[1:]:
        result = {
            "bookTitle": book_el.select("a")[0].text,
            "bookUrl": urljoin(url, book_el.select("a")[0].get("href")),
            "bookAuthor": book_el.select("td")[2].text
        }
        results.append(result)

    return results


def getDetails(url):
    url = url.replace("://wap.", "://www.")
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3"
    }
    response = requests.get(url, headers=headers, timeout=(5, 10))
    response.encoding = 'utf-8'
    soup = BeautifulSoup(response.text, 'html.parser')
    book = {
        "url": url,
        "title": soup.select("meta[property='og:novel:book_name']")[0].get("content"),
        "author": soup.select("meta[property='og:novel:author']")[0].get("content"),
        "intro": soup.select("meta[property='og:description']")[0].get("content"),
        "cover": soup.select("meta[property='og:image']")[0].get("content"),
        "chapterList": []
    }

    children = soup.select("#list a")
    for chapter_el in children:
        chapter = {
            "title": chapter_el.text,
            "url": urljoin(url, chapter_el.get("href"))
        }

        book["chapterList"].append(chapter)

    return book


def getChapterContent(url):
    response = requests.get(url, timeout=(5, 10))
    response.encoding = 'utf-8'
    soup = BeautifulSoup(response.text, 'html.parser')
    content = soup.select("#content")[0]
    content.select("#content_tip")[0].extract()
    content.select("p")[0].extract()


    return content.prettify()
