import json
import re
from urllib.parse import urljoin

from bs4 import BeautifulSoup
from log import log

from SessionManager import verification_activity_get
from WebViewClient import web_get

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


def _fetch(url):
    """站点直连 403，只能走 WebView；后台 WebView 失败再转前台人工验证"""
    def parse(result):
        data = json.loads(result)
        html = data.get("html", "")
        if not html:
            raise Exception("空页面")
        return html

    try:
        return parse(web_get(url))
    except Exception as e:
        log(f"dzsjob web_get 失败，转人工验证: {e}")
        return parse(verification_activity_get(url))


def verify(query):
    res = getDetails("http://www.dzsjob.com/268474_268474325/")
    res = getChapterContent(res["chapterList"][0]["url"])
    return 0 if len(res) > 10 else 1


def getSiteUrl():
    return "http://www.dzsjob.com"


def getDetails(url):
    soup = BeautifulSoup(_fetch(url), "html.parser")
    book = {
        "url": url,
        "title": "",
        "author": "",
        "intro": "",
        "cover": "",
        "chapterList": [],
    }

    title_el = soup.select_one("meta[property='og:novel:book_name']")
    author_el = soup.select_one("meta[property='og:novel:author']")
    intro_el = soup.select_one("meta[property='og:description']")
    cover_el = soup.select_one("meta[property='og:image']")
    if title_el:
        book["title"] = (title_el.get("content") or "").strip()
    if author_el:
        book["author"] = (author_el.get("content") or "").strip()
    if intro_el:
        book["intro"] = (intro_el.get("content") or "").strip()
    if cover_el:
        book["cover"] = urljoin(url, (cover_el.get("content") or "").strip())

    links = []
    for a in soup.select("ul li a"):
        title = a.get_text(strip=True)
        no = _chapter_no(title)
        if no is None:
            continue
        links.append((no, title, urljoin(url, a.get("href"))))

    # 顶部有“最新章节”倒序块，从第一个“第一章”开始截取
    first_index = next((i for i, (no, _, _) in enumerate(links) if no == 1), 0)
    seen_nos = set()
    for no, title, chapter_url in links[first_index:]:
        if no in seen_nos:
            continue
        seen_nos.add(no)
        book["chapterList"].append({
            "title": title,
            "url": chapter_url,
        })

    return book


def getChapterContent(url):
    soup = BeautifulSoup(_fetch(url), "html.parser")
    for selector in ("#content", ".content", ".showtxt", "#chaptercontent"):
        content = soup.select_one(selector)
        if content:
            return content.prettify()

    return ""
