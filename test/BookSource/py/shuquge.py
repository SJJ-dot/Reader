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
    # query = quote(query)
    url = f'https://www.2shuquge.net/search/'
    # searchkey=%E6%88%91%E7%9A%84&Submit=
    data = {"searchkey": query, "Submit": ""}
    # 发送get请求
    log(data)
    response = requests.post(url, data=data)
    response.encoding = 'utf-8'
    log(response.request)
    log(response.text)
    # 创建BeautifulSoup对象
    soup = BeautifulSoup(response.text, 'html.parser')
    books = []
    for bookEl in soup.select(".category-div"):
        books.append({
            "bookTitle": bookEl.select("a")[1].text,
            "bookUrl": urljoin(url, bookEl.select("a")[0].get("href")),
            "bookAuthor": bookEl.select("span")[0].text,
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
    response.encoding = "utf-8"
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

    for el in soup.select(".info-chapters")[1].select("a"):
        info["chapterList"].append({
            "title": el.text,
            "url": urljoin(book_url, el.get("href"))
        })
    return info


def getChapterContent(chapter_url):
    """
    获取章节内容
    :param chapter_url: 章节链接
    :return: 章节内容 html格式
    """
    # 发送get请求
    response = requests.get(chapter_url)
    response.encoding = "utf-8"
    # 创建BeautifulSoup对象
    soup = BeautifulSoup(response.text, 'html.parser')
    # 章节内容 html
    content = soup.select("#article")[0].prettify()

    if soup.select("#next_url")[0].text == "下一页":
        content += getChapterContent(urljoin(chapter_url, soup.select("#next_url")[0].get("href")))
    return content
