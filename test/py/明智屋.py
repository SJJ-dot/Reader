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
    query = query.encode('utf-8')
    # url encode
    query = quote(query)
    url = f'https://www.mingzw.net/mzwlist/{query}.html'
    # 发送get请求
    response = requests.get(url, timeout=(5, 10))
    response.encoding = 'utf-8'
    log(response.request.url)
    log(response.text)
    # 创建BeautifulSoup对象
    soup = BeautifulSoup(response.text, 'html.parser')
    books = []
    for bookEl in soup.select(".figure-horizontal"):
        books.append({
            "bookTitle": bookEl.select("a")[0].attrs["title"],
            "bookUrl": urljoin(url, bookEl.select("a")[0].get("href")),
            "bookAuthor": bookEl.select("dd")[0].text,
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
    response = requests.get(book_url, timeout=(5, 10))
    response.encoding = "utf-8"
    info = {}
    # 创建BeautifulSoup对象
    soup = BeautifulSoup(response.text, 'html.parser')
    info["url"] = book_url
    # 书名
    info["title"] = soup.select(".novel-name")[0].text.replace("《", "").replace("》", "")
    # 作者
    info["author"] = soup.select(".picinfo a")[0].text
    # 简介
    info["intro"] = soup.select(".content")[0].text
    # 封面
    info["cover"] = urljoin(book_url, soup.select(".pic img")[0].get("src"))
    # 章节列表
    info["chapterList"] = []
    chapterUrl = soup.select(".view-all-btn")[0].get("href")
    parseChapterList(book_url, urljoin(book_url, chapterUrl), info["chapterList"])

    return info


def parseChapterList(book_url, chapterUrl, chapterList):
    response = requests.get(chapterUrl, timeout=(5, 10))
    response.encoding = "utf-8"
    soup = BeautifulSoup(response.text, 'html.parser')
    # 递归加载章节列表
    dt = 0
    for el in soup.select(".content a"):
        chapterList.append({
            "title": el.text,
            "url": urljoin(chapterUrl, el.get("href"))
        })

    chapterList.pop(-1)
    chapterList.pop(-1)
    chapterList.pop(-1)


def getChapterContent(chapter_url):
    """
    获取章节内容
    :param chapter_url: 章节链接
    :return: 章节内容 html格式
    """
    # 发送get请求
    response = requests.get(chapter_url, timeout=(5, 10))
    response.encoding = "utf-8"
    # 创建BeautifulSoup对象
    soup = BeautifulSoup(response.text, 'html.parser')
    # 章节内容 html
    content = soup.select(".contents")[0].prettify()
    content = content[:content.rindex("推荐小说:")]
    return content
