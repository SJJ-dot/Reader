import re
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup
from requests.adapters import HTTPAdapter
from urllib3.util import Retry
from log import log

_UA = ("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
       "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
_HEADERS = {"User-Agent": _UA, "Accept-Language": "zh-CN,zh;q=0.9"}

# 复用连接，避免逐页新建 TLS 连接导致 SSL EOF / 变慢
_session = requests.Session()
_session.trust_env = False
_session.headers.update(_HEADERS)
_retry = Retry(total=2, backoff_factor=0.5, status_forcelist=[500, 502, 503, 504])
_adapter = HTTPAdapter(max_retries=_retry)
_session.mount("https://", _adapter)
_session.mount("http://", _adapter)
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


def _norm(text):
    return re.sub(r"[\s《》<>“”\"'·,，。.!！?？:：;；_\-—|/\\]+", "", text or "").lower()


def _book_id(url):
    match = re.search(r"/(?:xiaoshuo_|read/)(\d+)", url or "")
    return match.group(1) if match else ""


def verify(query):
    res = getDetails("https://m.x33yq.org/xiaoshuo_103619.html")
    res = getChapterContent(res["chapterList"][0]["url"])
    return 0 if len(res) > 10 else 1


def getSiteUrl():
    return "https://m.x33yq.org"


def _get(url):
    response = _session.get(url, timeout=30)
    response.encoding = "utf-8"
    response.raise_for_status()
    return BeautifulSoup(response.text, "html.parser")


def _meta(soup, prop, default=""):
    el = soup.select_one(f"meta[property='{prop}']")
    return (el.get("content") or default).strip() if el else default


def search(query):
    # 先访问首页拿会话，再提交搜索
    _session.get(getSiteUrl(), timeout=30)
    response = _session.post(
        urljoin(getSiteUrl(), "/search.html"),
        data={"searchkey": query},
        timeout=30,
    )
    response.encoding = "utf-8"
    soup = BeautifulSoup(response.text, "html.parser")

    key = _norm(query)
    books = []
    seen = set()
    for a in soup.select("a[href*='/xiaoshuo_']"):
        href = (a.get("href") or "").strip()
        book_id = _book_id(href)
        if not book_id or book_id in seen:
            continue
        parent_text = a.parent.get_text(" ", strip=True) if a.parent else ""
        parts = parent_text.split()
        if not parts:
            continue
        title = parts[0]
        if key and key not in _norm(title):
            continue
        seen.add(book_id)
        books.append({
            "bookTitle": title,
            "bookUrl": urljoin(getSiteUrl(), href),
            "bookAuthor": parts[1] if len(parts) > 1 else "",
        })

    return books


def getDetails(url):
    book_id = _book_id(url)
    if not book_id:
        return {"url": url, "title": "", "author": "", "intro": "",
                "cover": "", "chapterList": []}

    book_soup = _get(f"{getSiteUrl()}/xiaoshuo_{book_id}.html")
    cover = _meta(book_soup, "og:image")
    book = {
        "url": url,
        "title": _meta(book_soup, "og:novel:book_name"),
        "author": _meta(book_soup, "og:novel:author"),
        "intro": _meta(book_soup, "og:description"),
        "cover": urljoin(getSiteUrl(), cover) if cover else "",
        "chapterList": [],
    }

    chapters = {}
    for page in range(1, 51):
        page_url = f"{getSiteUrl()}/read/{book_id}/" if page == 1 \
            else f"{getSiteUrl()}/read/{book_id}_{page}/"
        soup = _get(page_url)

        added = 0
        for a in soup.select("a[href]"):
            title = a.get_text(strip=True)
            no = _chapter_no(title)
            if no is None or no in chapters:
                continue
            chapters[no] = {"title": title, "url": urljoin(page_url, a.get("href"))}
            added += 1

        has_next = any(a.get_text(strip=True) == "下一页" for a in soup.select("a[href]"))
        if added == 0 or not has_next:
            break

    # 站点目录本身就是升序，按插入顺序保留（个别“第一千零六章”是站点笔误，不能按数字排序）
    book["chapterList"] = list(chapters.values())
    return book


def getChapterContent(url):
    base = url[:-5] if url.endswith(".html") else url
    parts = []
    visited = set()
    current = url

    while current and current not in visited and len(parts) < 20:
        visited.add(current)
        soup = _get(current)

        content = soup.select_one(".content")
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
