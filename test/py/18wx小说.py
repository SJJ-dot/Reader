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
    res = getDetails("https://www.18wx.casa/book/2994")
    res = getChapterContent(res["chapterList"][0]["url"])
    return 0 if len(res) > 10 else 1


def getSiteUrl():
    return "https://www.18wx.casa"


def _norm(text):
    return re.sub(r"[\s《》<>“”\"'·,，。.!！?？:：;；_\-—|/\\]+", "", text or "").lower()


def search(query):
    response = _session.post(
        "https://www.18wx.casa/modules/article/search.php",
        data={"searchkey": query, "searchtype": "articlename"},
        headers=_HEADERS, timeout=30,
    )
    response.encoding = "utf-8"
    soup = BeautifulSoup(response.text, "html.parser")

    key = _norm(query)
    books = []
    seen = set()
    for a in soup.select("a[href*='/book/']"):
        href = (a.get("href") or "").strip()
        title = a.get_text(strip=True)
        if not title or not re.search(r"/book/\d+/?$", href):
            continue
        if key and key not in _norm(title):
            continue
        if href in seen:
            continue
        seen.add(href)
        books.append({
            "bookTitle": title,
            "bookUrl": href,
            "bookAuthor": "",
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
    last_no = 0
    last_arabic = None
    # ul.chaw_c 内是完整目录（升序），末尾可能带“新书已发”等非章节链接
    for a in soup.select("ul.chaw_c li a"):
        title = a.get_text(strip=True)
        no = _chapter_no(title)
        if no is None:
            continue
        # 站点会同时提供“第六百二十三章”和“第623章”两套；
        # 第二套从 380 重新开始且编号格式变化，遇到就结束
        is_arabic = _CHAPTER_RE.match(title).group(1).isdigit()
        if last_arabic is not None and is_arabic != last_arabic and no < last_no:
            break
        last_no = no
        last_arabic = is_arabic

        chapter_url = urljoin(url, a.get("href"))
        if chapter_url in seen or no in seen_nos:
            continue
        seen.add(chapter_url)
        seen_nos.add(no)
        book["chapterList"].append({"title": title, "url": chapter_url})

    return book


def getChapterContent(url):
    soup = _get(url)
    content = soup.select_one(".read-content")
    if not content:
        return ""

    # 去掉章节脚本和标题栏，避免正文里出现无关内容
    for tag in content.select("script, style, dt"):
        tag.decompose()

    return content.prettify()
