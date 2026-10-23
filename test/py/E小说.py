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
    res = getDetails("https://www.eexsvw.cc/zaigangzongchengweichuanshuo/")
    res = getChapterContent(res["chapterList"][0]["url"])
    return 0 if len(res) > 10 else 1


def getSiteUrl():
    return "https://www.eexsvw.cc"


def _pc_url(url):
    # 旧域名 m.zwda.com 会 302 到 m.eexsvw.cc，统一换成 PC 站
    url = (url or "")
    url = url.replace("://m.zwda.com", "://www.eexsvw.cc")
    url = url.replace("://www.zwda.com", "://www.eexsvw.cc")
    url = url.replace("://m.eexsvw.cc", "://www.eexsvw.cc")
    return re.sub(r"/index(_\d+)?\.html$", "/", url)


def _get(url):
    response = _session.get(_pc_url(url), timeout=30)
    response.encoding = "gbk"
    response.raise_for_status()
    return BeautifulSoup(response.text, "html.parser")


def _meta(soup, prop, default=""):
    el = soup.select_one(f"meta[property='{prop}']")
    return (el.get("content") or default).strip() if el else default


def getDetails(url):
    soup = _get(url)
    page_url = _pc_url(url)
    cover = _meta(soup, "og:image")
    book = {
        "url": url,
        "title": _meta(soup, "og:novel:book_name"),
        "author": _meta(soup, "og:novel:author"),
        "intro": _meta(soup, "og:description"),
        "cover": urljoin(page_url, cover) if cover else "",
        "chapterList": [],
    }

    seen_nos = set()
    for a in soup.select("a[href]"):
        title = a.get_text(strip=True)
        no = _chapter_no(title)
        if no is None or no in seen_nos:
            continue
        seen_nos.add(no)
        book["chapterList"].append({
            "title": title,
            "url": urljoin(page_url, a.get("href")),
        })

    return book


def getChapterContent(url):
    soup = _get(url)
    content = soup.select_one("#content")
    if not content:
        return ""

    return content.prettify()
