from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup
from log import log
from urllib.parse import quote


def search(query):
    """
    书源搜索函数
    :param query: 搜索关键词
    :return [{"bookTitle":"书名", "bookUrl": "书籍链接", "bookAuthor": "作者"}, ...] 搜索结果列表
    """
    # utf8编码
    # query = query.encode('utf-8')
    # url encode
    url = f"https://www.22biqu.com/search/"
    # POST请求的数据
    data = f'searchkey={quote(query)}&Submit={quote("搜索")}'
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Content-Type": "application/x-www-form-urlencoded",
    }
    # 发送POST请求Content-Type:
    response = requests.post(url, data=data, headers=headers)
    response.encoding = 'utf-8'
    log(response.request)
    log(response.text)
    # 创建BeautifulSoup对象 .querySelectorAll("div")[2].querySelectorAll("p")[0] document.querySelectorAll(".txt-list > li")
    soup = BeautifulSoup(response.text, 'html.parser')
    books = []

    for i, bookEl in enumerate(soup.select(".txt-list > li")):
        if i == 0:
            continue
        books.append({
            "bookTitle": bookEl.select("a")[0].text,
            "bookUrl": urljoin(url, bookEl.select("a")[0].attrs["href"]),
            "bookAuthor": bookEl.select(".s4")[0].text,
        })

    return books


def getDetails(book_url):
    """
    获取章节列表
    :param book_url: 书籍链接
    :return
    {"url": "书籍链接", "title": "书名", "author": "作者", "intro": "简介", "cover": "封面链接",
     "chapterList": [{"title": "章节名", "URL": "章节链接"}, ...]
     }
    """

    # url book_url
    # 发送get请求
    headers = {
        "User-Agent": "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36",
    }
    response = requests.get(book_url, headers=headers)
    response.encoding = "utf-8"
    log(response.request)
    log(response.text)
    info = {}
    # 创建BeautifulSoup对象
    soup = BeautifulSoup(response.text, 'html.parser')
    info["url"] = book_url
    # 书名
    info["title"] = soup.select("meta[property='og:novel:book_name']")[0].get("content")
    # 作者
    info["author"] = soup.select("meta[property='og:novel:author']")[0].get("content")
    # 简介
    info["intro"] = soup.select("meta[property='og:description']")[0].get("content")
    # 封面
    info["cover"] = soup.select("meta[property='og:image']")[0].get("content")
    # 章节列表
    info["chapterList"] = []
    loadChapterList(book_url, soup, info["chapterList"])
    return info


def loadChapterList(book_url, soup, chapterList):
    log("加载章节目录")
    for el in soup.select(".section-list")[1].select("a"):
        chapterList.append({
            "title": el.text,
            "url": urljoin(book_url, el.get("href"))
        })
    if soup.select(".index-container-btn")[1].text == "下一页":
        next_url = urljoin(book_url, soup.select(".index-container-btn")[1].get("href"))
        response = requests.get(next_url)
        response.encoding = "utf-8"
        soup = BeautifulSoup(response.text, 'html.parser')
        loadChapterList(book_url, soup, chapterList)


def getChapterContent(chapter_url):
    """
    获取章节内容
    :param chapter_url: 章节链接
    :return: 章节内容 html格式
    """
    # 发送get请求
    headers = {
        "User-Agent": "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36",
    }
    response = requests.get(chapter_url, headers=headers)
    response.encoding = "utf-8"
    # 创建BeautifulSoup对象
    log(response.request)
    log(response.text)
    soup = BeautifulSoup(response.text, 'html.parser')
    # 章节内容 html
    content = soup.select(".content")[0].prettify()
    # document.querySelectorAll('#next_url')[0]
    if soup.select("#next_url")[0].text == "下一页":
        next_url = urljoin(chapter_url, soup.select("#next_url")[0].get("href"))
        content += getChapterContent(next_url)

    return content


