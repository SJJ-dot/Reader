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
    response = requests.post(url, data=data, headers=headers, timeout=30)
    response.encoding = 'utf-8'
    soup = BeautifulSoup(response.text, 'html.parser')
    results = []

    # 只有一条结果时会直接跳转到详情页
    if response.url != url:
        box = soup.select_one(".itemtxt")
        if box:
            title_el = box.select_one("h1 a") or box.select_one("a")
            author_el = box.select_one("p a")
            results.append({
                "bookUrl": response.url,
                "bookTitle": title_el.text.strip() if title_el else "",
                "bookAuthor": author_el.text.replace("作者：", "").strip() if author_el else "",
            })
        return results

    # 列表结果：.itemtxt 为单本书容器
    for book_element in soup.select(".itemtxt"):
        title_el = book_element.select_one("h3 a")
        if not title_el:
            continue
        author_el = book_element.select_one("ul li")
        results.append({
            "bookTitle": title_el.text.strip(),
            "bookUrl": urljoin(url, title_el.get("href")),
            "bookAuthor": author_el.text.replace("作者：", "").strip() if author_el else "",
        })

    return results


def getDetails(url):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0",
    }
    log("get details:{}".format(url))
    response = requests.get(url, headers=headers, timeout=30)
    response.encoding = 'utf-8'
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
        response = requests.get(next_url, headers=headers, timeout=30)
        response.encoding = 'utf-8'
        html = response.text
        soup = BeautifulSoup(html, 'html.parser')
        get_chapter_list(next_url, soup, chapter_list)


def getChapterContent(url):
    response = requests.get(url, timeout=30)
    response.encoding = 'utf-8'
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    content = soup.select_one(".con").prettify()
    next_url_el = soup.select(".prenext a")[-1]
    if next_url_el and next_url_el.text == "下一页":
        next_url = urljoin(url, next_url_el.get("href"))
        return content + "\n" + getChapterContent(next_url)

    return content