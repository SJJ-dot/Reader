from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

from log import log

def verify():
    try:
        res = getDetails("https://www.22sq.net/b/1643/1643570/")
        res = getChapterContent(res["chapterList"][0]["url"])
        return len(res) > 10
    except Exception as e:
        log(f"Error :{e}")
        return False

def getSiteUrl():
    return "https://www.22sq.net/"


def search(query):
    # 搜索需要验证码 可以调用 verification_activity_get 函数
    return []


headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0",
}


def getDetails(book_url):
    # url book_url
    book_url = book_url.replace("://m.", "://www.")
    # 发送get请求
    response = requests.get(book_url, headers=headers, timeout=(5, 10))
    response.encoding = "utf-8"
    # log(response)
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

    while True:

        for el in soup.select(".section-list")[1].select("a"):
            info["chapterList"].append({
                "title": el.text,
                "url": urljoin(book_url, el.get("href"))
            })
        if soup.select(".index-container-btn")[1].text.strip() == "下一页":
            soup = BeautifulSoup(requests.get(urljoin(book_url, soup.select(".index-container-btn")[1].get("href")), headers=headers, timeout=(5, 10)).text, 'html.parser')
        else:
            break
    return info


def getChapterContent(chapter_url):
    """
    获取章节内容
    :param chapter_url: 章节链接
    :return: 章节内容 html格式
    """
    # 发送get请求
    response = requests.get(chapter_url, headers=headers, timeout=(5, 10))
    response.encoding = "utf-8"
    # 创建BeautifulSoup对象
    soup = BeautifulSoup(response.text, 'html.parser')
    # 章节内容 html
    content = soup.select("#content")[0].prettify()

    if soup.select("#next_url")[0].text.strip() == "下一页":
        content += getChapterContent(urljoin(chapter_url, soup.select("#next_url")[0].get("href")))
    return content


