import re
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

from log import log


_session = requests.Session()
_session.trust_env = False


def verify(query):
    res = getDetails("http://www.xshuquge.net/txt/60624/index.html")
    res = getChapterContent(res["chapterList"][0]["url"])
    return 0 if len(res) > 10 else 1


def getSiteUrl():
    return "http://www.xshuquge.net"


# def search(query):
#     # 搜索需要验证码 可以调用 verification_activity_get 函数
#     return []


headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0",
}


def getDetails(book_url):
    book_url = book_url.replace("://m.", "://www.")
    response = _session.get(book_url, headers=headers, timeout=(5, 15))
    response.encoding = "utf-8"
    soup = BeautifulSoup(response.text, 'html.parser')

    info = {
        "url": book_url,
        "title": soup.select_one("meta[property='og:novel:book_name']").get("content"),
        "author": soup.select_one("meta[property='og:novel:author']").get("content"),
        "intro": soup.select_one("meta[property='og:description']").get("content"),
        "cover": soup.select_one("meta[property='og:image']").get("content"),
        "chapterList": [],
    }

    # 目录只有一页，但 dl 内含“最新章节”和“正文卷”两段，只取“正文卷”之后避免重复
    in_body = False
    for el in soup.select(".listmain dl > *"):
        if el.name == "dt":
            in_body = "正文" in el.get_text()
            continue
        if not in_body:
            continue
        a = el.select_one("a")
        if not a or not a.get("href"):
            continue
        info["chapterList"].append({
            "title": a.get_text(strip=True),
            "url": urljoin(book_url, a.get("href")),
        })

    return info


def getChapterContent(chapter_url):
    """
    获取章节内容
    :param chapter_url: 章节链接
    :return: 章节内容 html格式
    """
    response = _session.get(chapter_url, headers=headers, timeout=(5, 15))
    response.encoding = "utf-8"
    soup = BeautifulSoup(response.text, 'html.parser')
    content = soup.select_one("#content")
    if not content:
        return ""

    for tip in content.select("#content_tip"):
        tip.extract()
    html = content.prettify()
    return re.sub(r"最新网址[:：]\s*\S+", "", html)


if __name__ == '__main__':
    url = "https://www.ishuqu.net/b/0/480/"
    print("================================")
    print("================================")
    print("================================")
    res = getDetails(url)
    print(res)

    print("================================")
    print("================================")
    print("================================")
    res = getChapterContent(res["chapterList"][0]["url"])
    print(res)
