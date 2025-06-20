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
    url = f"https://m.bixiange.me/e/search/indexpage.php"
    # POST请求的数据
    data = f'keyboard={quote(query, encoding="gbk")}&show=title&classid=0'
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Content-Type": "application/x-www-form-urlencoded",
    }
    # 发送POST请求Content-Type:
    response = requests.post(url, data=data, headers=headers, timeout=(5, 10))
    response.encoding = 'gbk'
    # log(response.request)
    # log(response.text)
    # 创建BeautifulSoup对象 .querySelectorAll("div")[2].querySelectorAll("p")[0] document.querySelectorAll(".txt-list > li")
    soup = BeautifulSoup(response.text, 'html.parser')
    books = []

    for i, bookEl in enumerate(soup.select(".list > .clearfix > li")):
        books.append({
            "bookTitle": bookEl.select("strong > a")[0].text,
            "bookUrl": urljoin(url, bookEl.select("strong > a")[0].attrs["href"])
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
    response = requests.get(book_url, headers=headers, timeout=(5, 10))
    response.encoding = "gbk"
    # log(response.request)
    # log(response.text)
    info = {}
    # 创建BeautifulSoup对象
    soup = BeautifulSoup(response.text, 'html.parser')
    info["url"] = book_url
    # 书名
    info["title"] = soup.select(".desc > h1")[0].text.split("(")[0]
    # 作者
    info["author"] = soup.select(".descTip > p")[1].text.replace("作者：", "")
    # 简介
    info["intro"] = soup.select(".descInfo")[0].text
    # 封面
    info["cover"] = urljoin(book_url, soup.select(".cover > img")[0].attrs["src"])
    # 章节列表
    info["chapterList"] = []
    loadChapterList(book_url, soup, info["chapterList"])
    return info


def loadChapterList(book_url, soup, chapterList):
    log("加载章节目录")
    for el in soup.select(".clearfix > li > a"):
        chapterList.append({
            "title": el.text,
            "url": urljoin(book_url, el.get("href"))
        })


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
    response = requests.get(chapter_url, headers=headers, timeout=(5, 10))
    response.encoding = "gbk"
    # 创建BeautifulSoup对象
    # log(response.request)
    # log(response.text)
    soup = BeautifulSoup(response.text, 'html.parser')
    # 章节内容 html
    content = soup.select(".content")[0].prettify()

    return content




