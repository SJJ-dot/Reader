import re

import requests
from bs4 import BeautifulSoup
from urllib.parse import urljoin, quote

from log import log


def search(query):
    base_url = "http://www.changshengrui.com/"
    url = f"{base_url}search/?searchkey={quote(query)}"
    response = requests.get(url, timeout=(5, 10))
    response.encoding = 'utf-8'
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    book_list = soup.select(".item")
    results = []

    for book_element in book_list:
        result = {
            "bookTitle": book_element.select_one("dl > dt > a").text,
            "bookUrl": urljoin(base_url, book_element.select_one("dl > dt > a").get("href")),
            "bookAuthor": "".join(book_element.select_one(".btm").find_all(string=True, recursive=False)).strip()
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
        "title": soup.select_one("#info > h1").text,
        "author": soup.select_one("#info > p:nth-child(2) > a").text,
        "intro": soup.select_one("#intro").prettify(),
        "cover": urljoin(url, soup.select_one("#fmimg > img").get("data-original")),
        "chapterList": []
    }

    chapter_url = urljoin(url, soup.select_one("#maininfo .chapterlist").get("href"))
    get_chapter_list(chapter_url, book["chapterList"])

    return book


def get_chapter_list(url, chapter_list):
    response = requests.get(url, timeout=(5, 10))
    response.encoding = 'utf-8'
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    chapter_list_el = soup.select("#list > dl > a")

    for chapter_el in chapter_list_el:
        chapter = {
            "title": chapter_el.text,
            "url": urljoin(url, chapter_el.get("href"))
        }
        chapter_list.append(chapter)

    next_page = soup.select_one(".right a")
    if next_page and next_page.text == "下一页":
        get_chapter_list(urljoin(url, next_page.get("href")), chapter_list)


def getChapterContent(url):
    response = requests.get(url, timeout=(5, 10))
    response.encoding = 'utf-8'
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    content = soup.select_one("#booktxt").prettify()
    next_url_el = soup.select(".next_url")[0]
    if next_url_el and next_url_el.text == "下一页":
        next_url = re.search(r"const\snext_page\s=\s'(.+)';", html).group(1)
        return content + "\n" + getChapterContent(next_url)

    return content


