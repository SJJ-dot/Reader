from urllib.parse import urljoin, quote

import requests
from bs4 import BeautifulSoup

from log import log


def isSupported(url):
    if "bvquge.com" in url:
        return True
    return False


def search(query):
    url = "https://www.bvquge.com/so/" + quote(query)
    response = requests.post(url, timeout=(5, 10))
    response.encoding = 'utf-8'
    log(response.request)
    log(response)
    soup = BeautifulSoup(response.text, 'html.parser')

    results = []
    book_list = soup.select(".item")
    for book_element in book_list:
        results.append({
            "bookTitle": book_element.select("a")[1].text.strip(),
            "bookUrl": urljoin(url, book_element.select("a")[1].get("href")),
            "bookAuthor": book_element.select("a")[2].text.replace("作者：", "").strip()
        })

    return results


def getDetails(url):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    }
    response = requests.get(url, headers=headers, timeout=(5, 10))
    response.encoding = 'uft-8'
    html = response.text
    log(response.request)
    log(response)
    soup = BeautifulSoup(html, 'html.parser')
    book = {
        "url": url,
        "title": soup.select_one(".booktxt h1").text.strip(),
        "author": soup.select_one(".booktxt a").text.split("者：")[1].strip(),
        "intro": soup.select_one(".des").text.strip(),
        "chapterList": []
    }

    soup = BeautifulSoup(html, 'html.parser')
    chapter_list_el = soup.select("#list a")
    for chapter_el in chapter_list_el:
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
    response.encoding = 'utf-8'
    log(response)
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    con = soup.select_one(".con")
    if con and con.h1:
        con.h1.decompose()
    content = con.prettify() if con else ""

    next_page = soup.select(".prenext a")[-1]
    if next_page and next_page.text == "下一页":
        content += "<br>"
        content += getChapterContent(urljoin(url, next_page.get("href")))

    return content


