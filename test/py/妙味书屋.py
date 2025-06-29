from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

from log import log


def isSupported(url):
    if "twinfoo.com" in url:
        return True
    return False


def search(query):
    results = []
    return results


def getDetails(url):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    }
    response = requests.get(url, headers=headers, timeout=(5, 10))
    response.encoding = 'utf8'
    html = response.text
    log(response.request)
    log(response)
    soup = BeautifulSoup(html, 'html.parser')
    book = {
        "url": url,
        "title": soup.select(".row h1")[0].get_text(strip=True),
        "author": "佚名",
        "intro": "",
        "chapterList": []
    }
    title_p1 = book["title"].split("(")[0].strip()
    if title_p1:
        book["title"] = title_p1

    chapter_list_el = soup.select(".row")[4].select(".col-md-4 a")
    for i, chapter_el in enumerate(chapter_list_el):
        chapter = {
            "title": chapter_el.text.strip(),
            "url": urljoin(url, chapter_el.get("href"))
        }
        book["chapterList"].append(chapter)

    return book


def getChapterContent(url):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    }
    response = requests.get(url, headers=headers, timeout=(5, 10))
    response.encoding = 'utf8'
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    page_a = soup.select("#page-links a")
    text = soup.select_one(".blurstxt").prettify()
    for a in page_a:
        response = requests.get(urljoin(url, a.get("href")), headers=headers, timeout=(5, 10))
        response.encoding = 'utf8'
        html = response.text
        soup = BeautifulSoup(html, 'html.parser')
        text += "<p></p>"
        text += soup.select_one(".blurstxt").prettify()

    return text


