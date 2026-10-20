import re
import time
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup
from log import log

_UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
       "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
_HEADERS = {"User-Agent": _UA, "Accept-Language": "zh-CN,zh;q=0.9"}
_SEARCH_HEADERS = {
    **_HEADERS,
    "Content-Type": "application/x-www-form-urlencoded",
    "Origin": "https://www.xbiquge345.com",
    "Referer": "https://www.xbiquge345.com/s.php",
}

# 详情/正文复用连接
_session = requests.Session()
_session.trust_env = False
_session.headers.update(_HEADERS)
_CHAPTER_RE = re.compile(r"^第\s*([0-9零一二三四五六七八九十百千两]+)\s*[章节]")
_CN_DIGITS = {"零": 0, "一": 1, "二": 2, "两": 2, "三": 3, "四": 4,
              "五": 5, "六": 6, "七": 7, "八": 8, "九": 9}
_CN_UNITS = {"十": 10, "百": 100, "千": 1000}


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
    res = getDetails("https://www.xbiquge345.com/book/44905/")
    res = getChapterContent(res["chapterList"][0]["url"])
    return 0 if len(res) > 10 else 1


def getSiteUrl():
    return "https://www.xbiquge345.com"


def search(query):
    if not query:
        return []

    # 站点同一会话连续搜索会被限流，每次用新会话直连；失败重试一次
    for _ in range(2):
        session = requests.Session()
        session.trust_env = False
        response = session.post(
            urljoin(getSiteUrl(), "/s.php"),
            data={"type": "articlename", "s": query, "submit": ""},
            headers=_SEARCH_HEADERS, timeout=30,
        )
        response.encoding = "utf-8"
        soup = BeautifulSoup(response.text, "html.parser")

        books = []
        seen = set()
        for a in soup.select("a[href*='/book/']"):
            href = (a.get("href") or "").strip()
            title = a.get_text(strip=True)
            if not title or href in seen:
                continue
            seen.add(href)
            books.append({
                "bookTitle": title,
                "bookUrl": urljoin(getSiteUrl(), href),
                "bookAuthor": "",
            })
        if books:
            return books
        time.sleep(1.5)

    return []


def _get(url):
    response = _session.get(url, timeout=30)
    response.encoding = "utf-8"
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
        "cover": urljoin(url, cover) if cover else "",
        "chapterList": [],
    }

    seen = set()
    seen_nos = set()
    # ul.info 内是完整目录（升序），其中夹杂“新书已发/完本感言”等非章节链接
    for a in soup.select("ul.info li a"):
        title = a.get_text(strip=True)
        no = _chapter_no(title)
        if no is None:
            continue
        chapter_url = urljoin(url, a.get("href"))
        if chapter_url in seen or no in seen_nos:
            continue
        seen.add(chapter_url)
        seen_nos.add(no)
        book["chapterList"].append({"title": title, "url": chapter_url})

    return book


def getChapterContent(url):
    soup = _get(url)
    content = soup.select_one("#txt")
    if not content:
        return ""

    # 去掉“一秒记住本站”广告
    for p in content.find_all("p"):
        if "一秒记住" in p.get_text():
            p.decompose()
            break

    return content.prettify()
