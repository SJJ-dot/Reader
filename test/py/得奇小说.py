from urllib.parse import urljoin, quote

import requests
from bs4 import BeautifulSoup

from log import log


def getSiteUrl():
    return "https://www.deqixs.org"


def search(query):
    url = f"https://www.deqixs.org/modules/article/search.php"
    # POST请求的数据
    data = f'searchkey={quote(query)}&action=login&searchtype=all&submit='
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0",
        "Content-Type": "application/x-www-form-urlencoded",
    }
    # 发送POST请求Content-Type:
    response = requests.post(url, data=data, headers=headers, timeout=(5, 10))
    response.encoding = 'utf-8'
    log(response)
    results = []
    if response.url != url:
        html = response.text
        soup = BeautifulSoup(html, 'html.parser')
        results.append({
            "bookUrl": response.url,
            "bookTitle": soup.select(".itemtxt a")[0].text,
            "bookAuthor": soup.select(".itemtxt a")[1].text.replace("作者：", ""),
        })
        pass
    else:
        html = response.text
        soup = BeautifulSoup(html, 'html.parser')
        book_list = soup.select(".bookbox")
        for book_element in book_list:
            result = {
                "bookTitle": book_element.select("a")[0].text,
                "bookUrl": urljoin(url, book_element.select("a")[0].get("href")),
                "bookAuthor": book_element.select(".author")[0].text.replace("作者：", "")
            }
            results.append(result)

    return results


def getDetails(url):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0",
    }
    log("get details:{}".format(url))
    response = requests.get(url, headers=headers, timeout=(5, 10))
    response.encoding = 'utf-8'
    log(response)
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    book = {
        "url": url,
        "title": soup.select(".itemtxt a")[0].text,
        "author": soup.select(".itemtxt a")[1].text.replace("作者：", ""),
        "intro": soup.select(".des")[0].prettify(),
        "cover": urljoin(url, soup.select("img")[0].get("src")),
        "chapterList": []
    }

    get_chapter_list(url, soup, book["chapterList"])

    return book


def get_chapter_list(url, soup, chapter_list):
    chapter_list_el = soup.select("#list a")

    for chapter_el in chapter_list_el:
        chapter = {
            "title": chapter_el.text,
            "url": urljoin(url, chapter_el.get("href"))
        }
        chapter_list.append(chapter)

    next_page = soup.select_one(".gr")
    if next_page and next_page.get("href"):
        next_url = urljoin(url, next_page.get("href"))
        if next_url == url:
            return
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0",
        }
        log(f"get chapter_list:{next_url}")
        response = requests.get(next_url, headers=headers, timeout=(5, 10))
        response.encoding = 'utf-8'
        html = response.text
        soup = BeautifulSoup(html, 'html.parser')
        get_chapter_list(next_url, soup, chapter_list)


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