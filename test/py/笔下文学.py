import re
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup
from log import log

_UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
       "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
_HEADERS = {"User-Agent": _UA, "Accept-Language": "zh-CN,zh;q=0.9"}
_CHAPTER_RE = re.compile(r"^第\s*([0-9零一二三四五六七八九十百千两]+)\s*[章节]")
_CN_DIGITS = {"零": 0, "一": 1, "二": 2, "两": 2, "三": 3, "四": 4,
              "五": 5, "六": 6, "七": 7, "八": 8, "九": 9}
_CN_UNITS = {"十": 10, "百": 100, "千": 1000}


_session = requests.Session()
_session.trust_env = False


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
    res = getDetails("http://www.bixia11.com/114622/")
    res = getChapterContent(res["chapterList"][0]["url"])
    return 0 if len(res) > 10 else 1


def getSiteUrl():
    # bixia11.com 会跳转到 boyaxs.cc，这里保留聚合搜索返回的域名以便归属
    return "http://www.bixia11.com"


def _norm(text):
    return re.sub(r"[\s《》<>“”\"'·,，。.!！?？:：;；_\-—|/\\]+", "", text or "").lower()


def search(query):
    response = _session.get(
        urljoin(getSiteUrl(), "/search07.html"),
        params={"searchkey": query, "searchtype": "all"},
        headers=_HEADERS, timeout=30,
    )
    response.encoding = "utf-8"
    soup = BeautifulSoup(response.text, "html.parser")

    key = _norm(query)
    books = []
    seen = set()
    for a in soup.select("a[href]"):
        href = (a.get("href") or "").strip()
        title = a.get_text(strip=True)
        if not re.match(r"^/\d+/?$", href) or not title:
            continue
        if key and key not in _norm(title):
            continue
        book_url = urljoin(getSiteUrl(), href)
        if book_url in seen:
            continue
        seen.add(book_url)

        parent_text = a.parent.get_text(" ", strip=True) if a.parent else title
        rest = parent_text.replace(title, "", 1).strip()
        author = rest.split()[0] if rest else ""

        books.append({
            "bookTitle": title,
            "bookUrl": book_url,
            "bookAuthor": author,
        })

    return books


def _get(url):
    response = _session.get(url, headers=_HEADERS, timeout=30)
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
    for a in soup.select("#catalog a"):
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
    parts = []
    visited = set()
    current = url

    while current and current not in visited:
        visited.add(current)
        soup = _get(current)

        content = soup.select_one(".content")
        if content:
            parts.append(content.prettify())

        next_url = ""
        for a in soup.select("a[href]"):
            if a.get_text(strip=True) == "下一页":
                next_url = urljoin(current, a.get("href"))
                break
        current = next_url

    return "\n".join(parts)
