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
    res = getDetails("http://www.bisge.cc/read/129463/9203908.html")
    res = getChapterContent(res["chapterList"][0]["url"])
    return 0 if len(res) > 10 else 1


def getSiteUrl():
    return "http://www.bisge.cc"


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

    catalog_url = ""
    for a in soup.select("a[href]"):
        if a.get_text(strip=True) == "目录":
            catalog_url = urljoin(url, a.get("href"))
            break
    if catalog_url:
        soup = _get(catalog_url)

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
    for a in soup.select("li a"):
        title = a.get_text(strip=True)
        no = _chapter_no(title)
        if no is None:
            continue
        links.append((no, title, urljoin(catalog_url or url, a.get("href"))))

    # 顶部有最新章节块，从第一个“第一章”开始截取
    first_index = next((i for i, (no, _, _) in enumerate(links) if no == 1), 0)
    seen_nos = set()
    for no, title, chapter_url in links[first_index:]:
        if no in seen_nos:
            continue
        seen_nos.add(no)
        book["chapterList"].append({"title": title, "url": chapter_url})

    return book


def getChapterContent(url):
    base = url[:-5] if url.endswith(".html") else url
    parts = []
    visited = set()
    current = url

    while current and current not in visited and len(parts) < 20:
        visited.add(current)
        soup = _get(current)

        content = soup.select_one(".read-content")
        if content:
            parts.append(content.prettify())

        next_url = ""
        for a in soup.select("a[href]"):
            full = urljoin(current, a.get("href"))
            if full.startswith(base + "_"):
                next_url = full
                break
        current = next_url

    return "\n".join(parts)
