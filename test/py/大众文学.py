import re
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup
from log import log

_UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
       "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
_HEADERS = {"User-Agent": _UA, "Accept-Language": "zh-CN,zh;q=0.9"}

# 复用连接；Cookie 验证通过后后续请求继续沿用
_session = requests.Session()
_session.trust_env = False
_session.headers.update(_HEADERS)
_CHAPTER_RE = re.compile(r"^第\s*([0-9零一二三四五六七八九十百千两]+)\s*[章]")
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
    res = getDetails("http://www.qzhd.cn/news/4201481.html")
    res = getChapterContent(res["chapterList"][-1]["url"])
    return 0 if len(res) > 10 else 1


def getSiteUrl():
    return "http://www.qzhd.cn"


def _open(url):
    """站点会先返回一段 JS 写 cookie 再 reload，这里模拟一次"""
    response = _session.get(url, timeout=30)
    response.encoding = "utf-8"

    match = re.search(r'document\.cookie\s*=\s*"([^"]+)"', response.text)
    if match:
        name, value = match.group(1).split(";")[0].split("=", 1)
        _session.cookies.set(name, value)
        response = _session.get(url, timeout=30)
        response.encoding = "utf-8"

    return _session, response


def _get(session, url):
    response = session.get(url, headers=_HEADERS, timeout=30)
    response.encoding = "utf-8"
    return BeautifulSoup(response.text, "html.parser")


def _clean_title(title):
    # 站点书名会带“ 作者txt”，去掉后与聚合搜索的书名保持一致
    return re.sub(r"\s+\S+txt$", "", (title or "").strip())


def _meta(soup, prop, default=""):
    el = soup.select_one(f"meta[property='{prop}']")
    return (el.get("content") or default).strip() if el else default


def getDetails(url):
    session, response = _open(url)
    soup = BeautifulSoup(response.text, "html.parser")

    raw_title = _meta(soup, "og:novel:book_name")
    if not raw_title:
        el = soup.select_one(".info h1")
        raw_title = el.get_text(strip=True) if el else ""

    cover = _meta(soup, "og:image")
    book = {
        "url": url,
        "title": _clean_title(raw_title),
        "author": _meta(soup, "og:novel:author"),
        "intro": _meta(soup, "og:description"),
        "cover": urljoin(url, cover) if cover else "",
        "chapterList": [],
    }

    chapters = {}

    def collect(page_soup, page_url):
        added = 0
        for a in page_soup.select("a[href]"):
            title = a.get_text(strip=True)
            no = _chapter_no(title)
            if no is None or no in chapters:
                continue
            chapters[no] = {"title": title, "url": urljoin(page_url, a.get("href"))}
            added += 1
        return added

    # 详情页含第 1~100 章和最新章节
    collect(soup, response.url)

    # “更多章节”按 /1/、/2/…… 分页加载，直到没有新增
    catalog_base = url[:-5] + "/" if url.endswith(".html") else url.rstrip("/") + "/"
    for page in range(1, 31):
        page_soup = _get(session, f"{catalog_base}{page}/")
        if not page_soup.select_one("a[href]"):
            break
        if collect(page_soup, f"{catalog_base}{page}/") == 0:
            break

    book["chapterList"] = [chapters[no] for no in sorted(chapters)]
    return book


def getChapterContent(url):
    session, response = _open(url)
    soup = BeautifulSoup(response.text, "html.parser")

    base = url[:-5] if url.endswith(".html") else url
    parts = []
    visited = set()
    current = url

    while current and current not in visited:
        visited.add(current)
        if current != url:
            soup = _get(session, current)

        block = soup.select_one(".word_read")
        if not block:
            break

        for tag in block.select("h3, .read_btn"):
            tag.decompose()

        html = (block.decode_contents()
                .replace("&amp;esp&amp;esp", "")
                .replace("&esp&esp", "")
                .strip())
        if html:
            parts.append(html)

        # 同一章可能分页为 xxx_1.html、xxx_2.html
        next_url = ""
        for a in soup.select("a[href]"):
            full = urljoin(current, a.get("href"))
            if full.startswith(base + "_"):
                next_url = full
                break
        current = next_url

    return "\n".join(parts)
