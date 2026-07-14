from urllib.parse import urljoin
import time

import requests
from bs4 import BeautifulSoup
from log import log


# 带重试的请求，应对 Cloudflare 限流和服务器不稳定
def _get(url, headers=None, timeout=(10, 30), retries=3):
    for attempt in range(retries):
        try:
            return requests.get(url, headers=headers, timeout=timeout)
        except (requests.exceptions.ConnectionError, requests.exceptions.ReadTimeout):
            if attempt == retries - 1:
                raise
            time.sleep(2)


# 支持搜索可以不需要实现verify
# def verify(query):
#     res = getDetails("https://www.kubixs.com/shu/xiaohuaxiangbaipiaowozhijiexigan/")
#     res = getChapterContent(res["chapterList"][0]["url"])
#     return 0 if len(res) > 10 else 1


def getSiteUrl():
    return "https://www.kubixs.com"


def search(query):
    session = requests.Session()
    session.headers.update({
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    })

    # 访问首页以获取 Cookie 和 _token
    response = _get(getSiteUrl(), headers=dict(session.headers), timeout=(10, 30))
    response.encoding = "utf-8"
    soup = BeautifulSoup(response.text, "html.parser")

    # 提取 CSRF _token
    token_input = soup.select_one("input[name='_token']")
    _token = token_input.get("value") if token_input else ""

    # 提交搜索
    data = {
        "keyword": query,
        "_token": _token,
    }
    headers = {
        "Content-Type": "application/x-www-form-urlencoded",
        "Origin": getSiteUrl(),
        "Referer": getSiteUrl(),
    }
    response = session.post(urljoin(getSiteUrl(), "/search"), data=data, headers=headers, timeout=(10, 30))
    response.encoding = "utf-8"
    soup = BeautifulSoup(response.text, "html.parser")
    books = []

    # 搜索结果在表格中，#nr 是第一行（空行），后续每行是一本书
    # 每行第一个 td 内的 a 标签包含书名和链接
    for row in soup.select("#nr ~ tr"):
        a = row.select_one("td a")
        if a:
            title = a.get_text().strip()
            href = a.get("href")
            if title and href:
                books.append({
                    "bookTitle": title,
                    "bookUrl": urljoin(getSiteUrl(), href),
                })

    return books


def getDetails(url):
    url = url.replace("://m.", "://www.")
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    }

    # 书籍详情页：通过 OG meta 标签获取书名、作者、简介、封面
    response = _get(url, headers=headers)
    response.encoding = "utf-8"
    soup = BeautifulSoup(response.text, "html.parser")
    book = {
        "url": url,
        "title": soup.select("meta[property='og:novel:book_name']")[0].get("content"),
        "author": soup.select("meta[property='og:novel:author']")[0].get("content"),
        "intro": soup.select("meta[property='og:description']")[0].get("content"),
        "cover": urljoin(url, soup.select("meta[property='og:image']")[0].get("content")),
        "chapterList": []
    }

    # 完整目录在 /list.html 页面，章节在 #readerlist > li > a
    list_url = urljoin(url, "list.html")
    response = _get(list_url, headers=headers)
    response.encoding = "utf-8"
    soup = BeautifulSoup(response.text, "html.parser")

    for chapter_element in soup.select("#readerlist li a"):
        title = chapter_element.get_text().strip()
        href = chapter_element.get("href")
        if title and href:
            book["chapterList"].append({
                "title": title,
                "url": urljoin(url, href)
            })

    return book


def getChapterContent(url):
    url = url.replace("://m.", "://www.")
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    }
    response = _get(url, headers=headers)
    response.encoding = "utf-8"
    html = response.text
    soup = BeautifulSoup(html, "html.parser")

    # 正文在 #content
    con = soup.select_one("#content")
    content = con.prettify()

    # 翻页：下一页链接在 div.jump 中，href 含 ?page=
    next_url = None
    for a in soup.select("div.jump a"):
        if "下一页" in a.get_text():
            href = a.get("href")
            if href:
                next_url = urljoin(url, href)
            break

    if next_url and "?page=" in next_url and next_url != url:
        content += getChapterContent(next_url)

    return content


if __name__ == '__main__':
    res = search("我的")
    print(res)

    print("================================")
    print("================================")
    print("================================")
    res = getDetails(res[0]["bookUrl"])
    print(res)

    print("================================")
    print("================================")
    print("================================")
    res = getChapterContent(res["chapterList"][0]["url"])
    print(res)
