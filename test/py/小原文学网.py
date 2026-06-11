from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup
from log import log

# 支持搜索可以不需要实现verify
# def verify(query):
#     res = getDetails("https://www.min-yuan.com/txt/wk/")
#     res = getChapterContent(res["chapterList"][0]["url"])
#     return 0 if len(res) > 10 else 1


def getSiteUrl():
    return "https://www.min-yuan.com"

def search(query):
    base_url = "https://www.min-yuan.com/search/"
    session = requests.Session()
    session.headers.update({
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6",
    })

    # 访问搜索页以获取必要的 Cookie
    session.get(base_url, timeout=(5, 10))

    data = {
        "searchkey": query
    }
    headers = {
        "Content-Type": "application/x-www-form-urlencoded",
        "Origin": "https://www.min-yuan.com",
        "Referer": base_url,
    }
    response = session.post(base_url, data=data, headers=headers, timeout=(5, 10))
    response.encoding = 'utf-8'
    soup = BeautifulSoup(response.text, 'html.parser')
    books = []

    for i, bookEl in enumerate(soup.select(".item")):
        books.append({
            "bookTitle": bookEl.select("a")[1].text,
            "bookUrl": urljoin(base_url, bookEl.select("a")[1].attrs["href"]),
        })
    return books


def getDetails(url):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    }
    response = requests.get(url, headers=headers, timeout=(5, 10))
    response.encoding = 'utf-8'
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    book = {
        "url": url,
        "title": soup.select("meta[property='og:novel:book_name']")[0].get("content"),
        "author": soup.select("meta[property='og:novel:author']")[0].get("content"),
        "intro": soup.select("meta[property='og:description']")[0].get("content"),
        "cover": urljoin(url, soup.select("meta[property='og:image']")[0].get("content")),
        "chapterList": []
    }
    chapter_elements = soup.select("#newlist a")
    for chapter_element in chapter_elements:
        book["chapterList"].append({
            "title": chapter_element.text.strip(),
            "url": urljoin(url, chapter_element.get("href"))
        })

    return book


def getChapterContent(url):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    }
    response = requests.get(url, headers=headers, timeout=(5, 10))
    response.encoding = 'utf-8'
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    con = soup.select_one("#booktxt")
    content = con.prettify()
    next_url = urljoin(url, soup.select("a[rel='next']")[0].get("href"))
    if "_" in next_url:
        content += getChapterContent(next_url)

    return content
