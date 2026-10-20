import re
from urllib.parse import quote, urljoin

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


def _norm(text):
    return re.sub(r"[\s《》<>“”\"'·,，。.!！?？:：;；_\-—|/\\]+", "", text or "").lower()


def verify(query):
    res = getDetails("https://www.xs8.cn/book/18002156508922604")
    res = getChapterContent(res["chapterList"][0]["url"])
    return 0 if len(res) > 10 else 1


def getSiteUrl():
    return "https://www.xs8.cn"


def _get(url):
    response = _session.get(url, timeout=30)
    response.encoding = "utf-8"
    response.raise_for_status()
    return BeautifulSoup(response.text, "html.parser")


def _meta(soup, prop, default=""):
    el = soup.select_one(f"meta[property='{prop}']")
    return (el.get("content") or default).strip() if el else default


def search(query):
    if not query:
        return []

    response = _session.get(
        urljoin(getSiteUrl(), "/search"),
        params={"kw": query},
        timeout=30,
    )
    response.encoding = "utf-8"
    soup = BeautifulSoup(response.text, "html.parser")

    key = _norm(query)
    books = []
    seen = set()
    for a in soup.select("a[href*='/book/']"):
        href = (a.get("href") or "").strip()
        title = a.get_text(strip=True)
        if not title or not href:
            continue
        if key and key not in _norm(title):
            continue
        book_url = urljoin(getSiteUrl(), href)
        if book_url in seen:
            continue
        seen.add(book_url)
        books.append({
            "bookTitle": title,
            "bookUrl": book_url,
            "bookAuthor": "",
        })

    return books


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

    catalog_url = ""
    for a in soup.select("a[href*='/chapterlist/']"):
        catalog_url = urljoin(url, a.get("href"))
        break

    catalog_soup = _get(catalog_url) if catalog_url else soup

    seen_nos = set()
    for a in catalog_soup.select("a[href]"):
        title = a.get_text(strip=True)
        no = _chapter_no(title)
        if no is None or no in seen_nos:
            continue
        seen_nos.add(no)
        book["chapterList"].append({
            "title": title,
            "url": urljoin(catalog_url or url, a.get("href")),
        })

    return book


def getChapterContent(url):
    soup = _get(url)
    content = soup.select_one(".read-content")
    if not content:
        return ""

    # 去掉内嵌样式/脚本和章节提示，只保留正文
    for tag in content.select("style, script, .j_chapterTip, .chapter-tip"):
        tag.decompose()

    return content.prettify()
