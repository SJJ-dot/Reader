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
    url = 'https://www.shoujixs.net/modules/article/search.php'
    # POST请求的数据
    data = {'searchkey': query.encode('gbk')}
    # 发送POST请求
    response = requests.post(url, data=data)
    response.encoding = 'gbk'
    log(response.text)
    # 创建BeautifulSoup对象
    soup = BeautifulSoup(response.text, 'html.parser')
    books = []
    for c_row in soup.select(".c_row"):
        books.append({
            "bookTitle": c_row.select(".c_subject")[0].select("a")[0].text,
            "bookUrl": urljoin(url, c_row.select(".c_subject")[0].select("a")[0].get("href")),
            "bookAuthor": c_row.select(".c_value")[0].text,
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
    info["url"] = soup.select("meta[property='og:url']")[0].get("content")
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
    parseChapterList(book_url, soup, info["chapterList"])

    return info


def parseChapterList(book_url, soup, chapterList):
    # 递归加载章节列表
    dt = 0
    for el in soup.select("#lbks dl")[0].children:
        if el.name == "dt":
            dt = dt + 1

        elif el.name == "dd":
            if dt < 2:
                continue
            chapterList.append({
                "title": el.select("a")[0].text,
                "url": urljoin(book_url, el.select("a")[0].get("href"))
            })

    if soup.select(".right a")[0].has_attr("href"):
        next_url = urljoin(book_url, soup.select(".right a")[0].get("href"))
        response = requests.get(next_url)
        response.encoding = "gbk"
        soup = BeautifulSoup(response.text, 'html.parser')
        parseChapterList(book_url, soup, chapterList)


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
    content = soup.select("#zjny")[0].prettify()
    return content


if __name__ == '__main__':
    result = getDetails("https://www.shoujixs.net/shoujixs_13952/")
    log(result)
