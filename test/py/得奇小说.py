from urllib.parse import urljoin, quote

import requests
from bs4 import BeautifulSoup

from log import log


def search(query):
    # https://www.deqixs.com/tag/?key=%E8%B5%A4%E5%BF%83%E5%B7%A1%E5%A4%A9
    url = f"https://www.deqixs.com/tag/?key={quote(query)}"
    response = requests.get(url, timeout=(5, 10))
    response.encoding = 'utf-8'
    log(response)
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    book_list = soup.select(".item")
    results = []

    for book_element in book_list:
        result = {
            "bookTitle": book_element.select("a")[1].text,
            "bookUrl": urljoin(url, book_element.select_one("a").get("href")),
            "bookAuthor": book_element.select("a")[2].text.replace("作者：", "")
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
        "title": soup.select(".item a")[1].text,
        "author": soup.select(".item a")[2].text.replace("作者：", ""),
        "intro": soup.select_one(".des").prettify(),
        "cover": urljoin(url, soup.select_one(".item a img").get("src")),
        "chapterList": []
    }

    get_chapter_list(url, soup, book["chapterList"])

    return book


def get_chapter_list(url, soup, chapter_list):
    chapter_list_el = soup.select("#list ul a")

    for chapter_el in chapter_list_el:
        chapter = {
            "title": chapter_el.text,
            "url": urljoin(url, chapter_el.get("href"))
        }
        chapter_list.append(chapter)

    next_page = soup.select_one(".gr")
    if next_page and next_page.get("href"):
        url = urljoin(url, next_page.get("href"))
        response = requests.get(url, timeout=(5, 10))
        response.encoding = 'utf-8'
        html = response.text
        soup = BeautifulSoup(html, 'html.parser')
        get_chapter_list(url, soup, chapter_list)


def getChapterContent(url):
    response = requests.get(url, timeout=(5, 10))
    response.encoding = 'utf-8'
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    content = soup.select_one(".con").prettify()
    next_url_el = soup.select(".prenext a")[-1]
    if next_url_el and next_url_el.text == "下一页":
        next_url = urljoin(url, next_url_el.get("href"))
        return content + "\n" + getChapterContent(next_url)

    return content


