import re
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup
from log import log

_UA = ("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
       "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
_HEADERS = {"User-Agent": _UA, "Accept-Language": "zh-CN,zh;q=0.9"}
_CHAPTER_RE = re.compile(r"^第\s*([0-9零一二三四五六七八九十百千两]+)\s*[章节]")
_CN_DIGITS = {"零": 0, "一": 1, "二": 2, "两": 2, "三": 3, "四": 4,
              "五": 5, "六": 6, "七": 7, "八": 8, "九": 9}
_CN_UNITS = {"十": 10, "百": 100, "千": 1000}

_session = requests.Session()
_session.trust_env = False
_session.headers.update(_HEADERS)


def _chapter_no(title):
    match = _CHAPTER_RE.match(title or "")
    if not match:
        return None
    text = match.group(1)
    if text.isdigit():
        return int(text)
    total = 0
    num = 0
    for char in text:
        if char in _CN_DIGITS:
            num = _CN_DIGITS[char]
        elif char in _CN_UNITS:
            if num == 0:
                num = 1
            total += num * _CN_UNITS[char]
            num = 0
    return total + num


def verify(query):
    res = getDetails("http://www.b5200.org/139_139744/")
    res = getChapterContent(res["chapterList"][0]["url"])
    return 0 if len(res) > 10 else 1


def getSiteUrl():
    return "http://www.b5200.org"


def _get(url):
    response = _session.get(url, timeout=30)
    # 站点是 GBK 编码
    response.encoding = "gbk"
    response.raise_for_status()
    return BeautifulSoup(response.text, "html.parser")


def _meta(soup, prop, default=""):
    el = soup.select_one(f"meta[property='{prop}']")
    return (el.get("content") or default).strip() if el else default


def getDetails(url):
    soup = _get(url)
    cover = _meta(soup, "og:image")
    book = {
        "url": url,
        "title": _meta(soup, "og:novel:book_name"),
        "author": _meta(soup, "og:novel:author"),
        "intro": _meta(soup, "og:description"),
        "cover": cover,
        "chapterList": [],
    }

    links = []
    for a in soup.select("a[href]"):
        title = a.get_text(strip=True)
        no = _chapter_no(title)
        if no is None:
            continue
        links.append((no, title, urljoin(url, a.get("href"))))

    # 从第一个“第一章”开始截取
    first_index = next((i for i, (no, _, _) in enumerate(links) if no == 1), 0)
    seen_nos = set()
    for no, title, chapter_url in links[first_index:]:
        if no in seen_nos:
            continue
        seen_nos.add(no)
        book["chapterList"].append({"title": title, "url": chapter_url})

    return book


def getChapterContent(url):
    soup = _get(url)
    content = soup.select_one("#content")
    if not content:
        return ""

    return content.prettify()
