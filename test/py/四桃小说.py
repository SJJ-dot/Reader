from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup


def search(query):
    url = f"https://www.4txs.com/search.html"
    response = requests.post(url, data={"searchkey": query}, timeout=(5, 10))
    response.encoding = 'utf-8'
    soup = BeautifulSoup(response.text, 'html.parser')
    book_list = soup.select(".library > li")
    results = []

    for book_element in book_list:
        result = {
            "bookTitle": book_element.select_one(".bookname").text,
            "bookUrl": urljoin(url, book_element.select_one(".bookname").get("href")),
            "bookAuthor": "".join(book_element.select_one(".author").get("href")).strip()
        }
        results.append(result)

    return results


def getDetails(url):
    response = requests.get(url, timeout=(5, 10))
    response.encoding = 'utf-8'
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    book = {"url": url,
            "title": soup.select("meta[property='og:novel:book_name']")[0].get("content"),
            "author": soup.select("meta[property='og:novel:author']")[0].get("content"),
            "intro": soup.select("meta[property='og:description']")[0].get("content"),
            "cover": soup.select(".detail .bookimg img")[0].get("src"),
            "chapterList": []}

    chapter_url = urljoin(url, soup.select_one(".detail .action a").get("href"))
    get_chapter_list(chapter_url, book["chapterList"])

    return book


def get_chapter_list(url, chapter_list):
    response = requests.get(url, timeout=(5, 10))
    response.encoding = 'utf-8'
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    chapter_list_el = soup.select(".read > dl")[1].select("a")

    for chapter_el in chapter_list_el:
        chapter = {
            "title": chapter_el.text,
            "url": urljoin(url, chapter_el.get("href"))
        }
        chapter_list.append(chapter)


def getChapterContent(url):
    response = requests.get(url, timeout=(5, 10))
    response.encoding = 'utf-8'
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    content = soup.select_one("#content").prettify()
    next_url_el = soup.select(".page a")[2]
    if next_url_el and "下一页" in next_url_el.text:
        next_url = urljoin(url, next_url_el.get("href"))
        return content + "\n" + getChapterContent(next_url)

    return content


