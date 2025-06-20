from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup
from log import log


# {
#   "bookSourceComment": "由 @NPCDW 分享（#85",
#   "bookSourceGroup": "",
#   "bookSourceName": "天天看小说",
#   "bookSourceType": 0,
#   "bookSourceUrl": "https://cn.ttkan.co",
#   "bookUrlPattern": "",
#   "customOrder": 23,
#   "enabled": true,
#   "enabledCookieJar": false,
#   "enabledExplore": false,
#   "exploreUrl": "",
#   "header": "",
#   "lastUpdateTime": 1687361913056,
#   "loginUi": "",
#   "loginUrl": "",
#   "respondTime": 21167,
#   "ruleBookInfo": {
#     "author": "//meta[@name='og:novel:author']/@content",
#     "coverUrl": "//meta[@name='og:image']/@content",
#     "downloadUrls": "",
#     "intro": "//meta[@name='og:description']/@content",
#     "kind": "//meta[@name='og:novel:category']/@content&&//meta[@name='og:novel:status']/@content",
#     "lastChapter": "//meta[@name='og:novel:latest_chapter_name']/@content",
#     "name": "//meta[@name='og:novel:book_name']/@content",
#     "tocUrl": ""
#   },
#   "ruleContent": {
#     "content": "class.content@tag.p@text",
#     "nextContentUrl": "",
#     "replaceRegex": ""
#   },
#   "ruleExplore": {},
#   "ruleReview": {},
#   "ruleSearch": {
#     "author": "tag.li.1@text",
#     "bookList": "class.novel_cell",
#     "bookUrl": "tag.a.0@href",
#     "coverUrl": "tag.amp-img.0@src",
#     "intro": "tag.li.2@text",
#     "kind": "",
#     "lastChapter": "",
#     "name": "tag.h3.0@text"
#   },
#   "ruleToc": {
#     "chapterList": "class.full_chapters@children[0]@tag.a",
#     "chapterName": "text",
#     "chapterUrl": "href",
#     "preUpdateJs": "",
#     "updateTime": ""
#   },
#   "searchUrl": "/novel/search?q={{key}}",
#   "weight": 0
# }
def search(query):
    """
    书源搜索函数
    :param query: 搜索关键词
    :return [{"bookTitle":"书名", "bookUrl": "书籍链接", "bookAuthor": "作者"}, ...] 搜索结果列表
    """
    # 请求的URL
    url = 'https://cn.ttkan.co/novel/search'
    # 请求的数据
    data = {'q': query.encode('utf-8')}
    # 发送get请求
    response = requests.get(url, params=data, timeout=(5, 10))
    response.encoding = 'utf-8'
    log(response.text)
    #   "ruleSearch": {
    #     "author": "tag.li.1@text",
    #     "bookList": "class.novel_cell",
    #     "bookUrl": "tag.a.0@href",
    #     "coverUrl": "tag.amp-img.0@src",
    #     "intro": "tag.li.2@text",
    #     "kind": "",
    #     "lastChapter": "",
    #     "name": "tag.h3.0@text"
    #   },
    # 创建BeautifulSoup对象
    soup = BeautifulSoup(response.text, 'html.parser')
    books = []
    for bookEl in soup.select(".novel_cell"):
        books.append({
            "bookTitle": bookEl.select("h3")[0].text,
            "bookUrl": urljoin(url, bookEl.select("a")[0].get("href")),
            "bookAuthor": bookEl.select("li")[1].text.replace("作者：", ""),
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
    #   "ruleBookInfo": {
    #     "author": "//meta[@name='og:novel:author']/@content",
    #     "coverUrl": "//meta[@name='og:image']/@content",
    #     "downloadUrls": "",
    #     "intro": "//meta[@name='og:description']/@content",
    #     "kind": "//meta[@name='og:novel:category']/@content&&//meta[@name='og:novel:status']/@content",
    #     "lastChapter": "//meta[@name='og:novel:latest_chapter_name']/@content",
    #     "name": "//meta[@name='og:novel:book_name']/@content",
    #     "tocUrl": ""
    #   }
    info["url"] = book_url
    # 书名
    info["title"] = soup.select("meta[name='og:novel:book_name']")[0].get("content")
    # 作者
    info["author"] = soup.select("meta[name='og:novel:author']")[0].get("content")
    # 简介
    info["intro"] = soup.select("meta[name='og:description']")[0].get("content")
    # 封面
    info["cover"] = soup.select("meta[name='og:image']")[0].get("content")
    # 章节列表
    info["chapterList"] = []
    #   "ruleToc": {
    #     "chapterList": "class.full_chapters@children[0]@tag.a",
    #     "chapterName": "text",
    #     "chapterUrl": "href",
    #     "preUpdateJs": "",
    #     "updateTime": ""
    #   }
    for el in soup.select(".full_chapters")[0].findChildren("div")[0].findChildren("a"):
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
    #   "ruleContent": {
    #     "content": "class.content@tag.p@text",
    #     "nextContentUrl": "",
    #     "replaceRegex": ""
    #   },
    # 发送get请求
    response = requests.get(chapter_url, timeout=(5, 10))
    response.encoding = "utf-8"
    log(response.text)
    # 创建BeautifulSoup对象
    soup = BeautifulSoup(response.text, 'html.parser')
    # 章节内容 html
    txt = ""
    for p in soup.select(".content p"):
        txt = txt + "<br>\n" + p.prettify()
    return txt
