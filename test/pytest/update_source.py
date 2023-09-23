import base64
import gzip
import json
import os

from pypinyin import pinyin, Style


def to_pinyin(name):
    pinyin_list = pinyin(name, style=Style.NORMAL)
    pinyin_list = ''.join([item[0] for item in pinyin_list])
    return pinyin_list


def write_source(name, script):
    # 将汉字转换为拼音
    pinyin_list = to_pinyin(name)
    print(f"{name} -> {pinyin_list}")
    # 将脚本写入文件
    with open(f"../BookSource/py/{pinyin_list}.py", "w", encoding="utf-8") as f:
        f.write(script)


def update_json():
    # 读取json文件
    with open("../BookSource/default.json", "r", encoding="utf-8") as f:
        json1 = f.read()
    # 将json字符串转换为字典
    json_dict = json.loads(json1)
    pySourceDict = {}
    for item in json_dict["pySource"]:
        pySourceDict[to_pinyin(item["source"])] = item

    for item in os.listdir("../BookSource/py"):
        with open(f"../BookSource/py/{item}", "r", encoding="utf-8") as f:
            py = f.read()
        if item.replace(".py", "") not in pySourceDict:
            json_dict["pySource"].append({
                "source": item.replace(".py", ""),
                "js": py,
                "version": 1,
                "original": False,
                "enable": True,
                "requestDelay": -1,
                "website": ""
            })
        else:
            source = pySourceDict[item.replace(".py", "")]
            if source["js"] != py:
                source["js"] = py
                source["version"] = source["version"] + 1

    # 将字典转换为json字符串 并写入文件
    with open("../BookSource/default.json", "w", encoding="utf-8") as f:
        f.write(json.dumps(json_dict, indent=4, ensure_ascii=False))

    # 将字典转换为json字符串 使用gzip压缩并转换base64 后 写入文件
    with open("../BookSource/default.json.gzip", 'w', encoding="utf-8") as f:
        f.write(base64.b64encode(gzip.compress(json.dumps(json_dict).encode("utf-8"))).decode("utf-8"))


if __name__ == '__main__':
    write_source("圣墟小说网", '''from urllib.parse import urljoin

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
    url = 'http://www.shengxuxu.net/search.html?searchtype=novelname&searchkey='
    # POST请求的数据
    data = {'searchkey': query.encode('utf-8'), "searchtype": 'novelname'}
    # 发送get请求
    response = requests.get(url, params=data)
    response.encoding = 'utf-8'
    log(response.text)
    # 创建BeautifulSoup对象
    soup = BeautifulSoup(response.text, 'html.parser')
    books = []
    for bookEl in soup.select(".librarylist > li"):
        books.append({
            "bookTitle": bookEl.select(".novelname")[0].text,
            "bookUrl": urljoin(url, bookEl.select(".novelname")[0].get("href")),
            "bookAuthor": bookEl.select(".info span")[1].text.replace("作者：",""),
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
    # document.querySelectorAll(".dirlist")[1].querySelectorAll("a")[1664]
    for el in soup.select(".dirlist")[1].select("a"):
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
    content = soup.select("#chaptercontent")[0].prettify()
    return content
''')
    update_json()
