from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup
from log import log


def search(query):
    """
    书源搜索函数
    :param query: 搜索关键词
    :return [{"bookTitle":"书名", "bookUrl": "书籍链接", "bookAuthor": "作者"}, ...] 搜索结果列表
    """
    # POST请求的URL
    url = 'https://m.boshishuwu.com/s.php'
    # POST请求的数据
    data = {'keyword': query.encode('utf-8'), "t": 1}
    # 发送POST请求
    response = requests.post(url, data=data)
    response.encoding = 'utf-8'
    log(response.text)
    # 创建BeautifulSoup对象
    soup = BeautifulSoup(response.text, 'html.parser')
    books = []
    for c_row in soup.select(".hot_sale"):
        books.append({
            "bookTitle": c_row.select(".title")[0].text,
            "bookUrl": urljoin(url, c_row.select("a")[0].get("href").replace("://m.", "://www.")),
            "bookAuthor": c_row.select(".author")[0].text.split(" | ")[1].replace("作者：", ""),
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
    response = requests.get(book_url)
    response.encoding = "gbk"
    info = {}
    # 创建BeautifulSoup对象
    soup = BeautifulSoup(response.text, 'html.parser')
    info["url"] = book_url
    # 书名
    info["title"] = soup.select("#info h1")[0].text
    # 作者 作    者：黄金国巫妖
    info["author"] = soup.select("#info p")[0].text.split("：")[1]
    # 简介
    info["intro"] = soup.select("#intro")[0].prettify()
    # 封面
    # info["cover"] = soup.select("#fmimg img")[0].attrs["src"]
    # 章节列表
    info["chapterList"] = []
    parseChapterList(book_url, soup, info["chapterList"])

    return info


def parseChapterList(book_url, soup, chapterList):
    # 递归加载章节列表
    dt = 0
    for el in soup.select("#list dl")[0].children:
        if el.name == "dt":
            dt = dt + 1
            continue
        if dt < 3:
            continue
        if el.name == "dd":
            chapterList.append({
                "title": el.select("a")[0].text,
                "url": urljoin(book_url, el.select("a")[0].get("href"))
            })


def getChapterContent(chapter_url):
    """
    获取章节内容
    :param chapter_url: 章节链接
    :return: 章节内容 html格式
    """
    # 发送get请求
    response = requests.get(chapter_url)
    response.encoding = "gbk"
    # 创建BeautifulSoup对象
    soup = BeautifulSoup(response.text, 'html.parser')
    # 章节内容 html
    content = soup.select("#content")[0].prettify()
    return content
