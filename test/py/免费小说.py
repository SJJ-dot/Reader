import json
import re
from urllib.parse import urljoin, quote

from bs4 import BeautifulSoup, NavigableString
from log import log

from SessionManager import verification_activity_get
from WebViewClient import web_get


# 站点直连会触发 Cloudflare 人机验证，直接走 WebView
_VERIFY_MARKERS = (
    "challenges.cloudflare.com",
    "此验证用于防止机器人自动化访问",
    "Just a moment",
)


def _is_verify(html):
    return any(marker in html for marker in _VERIFY_MARKERS)


def _fetch(url):
    """直接走 WebView；后台 WebView 被拦截时转前台人工验证"""
    def parse(result):
        result = json.loads(result)
        html = result.get("html", "")
        if _is_verify(html):
            raise Exception("人机验证")
        return html

    try:
        return parse(web_get(url))
    except Exception as e:
        log(f"web_get 失败，转人工验证: {e}")
        return parse(verification_activity_get(url))


def getSiteUrl():
    return "https://cn.zhys.tw"


def search(query):
    url = urljoin(getSiteUrl(), f"/search?q={quote(query)}")
    html = _fetch(url)
    soup = BeautifulSoup(html, "html.parser")
    books = []

    # 搜索结果在 ul.space-y-4 > li[itemprop='mainEntity'] > a[itemprop='url']
    for li in soup.select("ul.space-y-4 li[itemprop='mainEntity']"):
        author_el = li.select_one("[itemprop='author']")
        a = li.select_one("a[itemprop='url']")
        if a:
            href = a.get("href")
            title_el = a.select_one("h2[itemprop='name']")
            title = title_el.get_text(strip=True) if title_el else ""
            if title and href:
                books.append({
                    "bookTitle": title,
                    "bookAuthor": author_el.get_text(strip=True) if author_el else "",
                    "bookUrl": urljoin(getSiteUrl(), href),
                })

    return books


def getDetails(url):
    html = _fetch(url)
    soup = BeautifulSoup(html, "html.parser")

    title_el = soup.select_one("h1")
    author_el = soup.select_one("[itemprop='author']")
    intro_el = soup.select_one("meta[property='og:description']")
    cover_el = soup.select_one("meta[property='og:image']")

    book = {
        "url": url,
        "title": title_el.get_text(strip=True) if title_el else "",
        "author": author_el.get_text(strip=True) if author_el else "",
        "intro": intro_el.get("content", "") if intro_el else "",
        "cover": urljoin(url, cover_el.get("content", "")) if cover_el else "",
        "chapterList": []
    }

    # 章节列表在 a.chp-link（排除继续阅读等导航按钮）
    chapter_links = soup.select("a.chp-link")
    seen = set()
    chapters = []
    for a in chapter_links:
        href = a.get("href")
        if not href or href in seen:
            continue
        seen.add(href)
        # get_text 对部分链接失效，统一用 NavigableString 取第一个文本节点
        title = ""
        for desc in a.descendants:
            if isinstance(desc, NavigableString) and desc.strip():
                title = desc.strip()
                break
        if title and title not in ("继续阅读", "开始阅读"):
            chapters.append({
                "title": title,
                "url": urljoin(url, href)
            })

    # 页面顺序非正序，按 href 中的章节 ID 排序
    def _chap_id(c):
        m = re.search(r"/(\d+)\.html", c["url"])
        return int(m.group(1)) if m else 0
    chapters.sort(key=_chap_id)
    book["chapterList"] = chapters

    return book


def getChapterContent(url):
    html = _fetch(url)
    soup = BeautifulSoup(html, "html.parser")

    # 正文在 #article-content（class reader-text）
    con = soup.select_one("#article-content")
    if not con:
        con = soup.select_one(".reader-text")
    if not con:
        con = soup.select_one("article")

    if not con:
        return ""

    # 删除广告容器
    for ad in con.select(".ad-container"):
        ad.decompose()

    return con.prettify()


if __name__ == '__main__':
    res = search("我的")
    # print(res)
    for book in res:
        print(book)

    print("================================")
    print("================================")
    print("================================")
    res = getDetails(res[0]["bookUrl"])
    # print(res)
    print("url:", res["url"])
    print("title:", res["title"])
    print("author:", res["author"])
    print("intro:", res["intro"])
    print("cover:", res["cover"])
    for c in res["chapterList"]:
        print("chapter:", c["title"], c["url"])

    print("================================")
    print("================================")
    print("================================")
    res = getChapterContent(res["chapterList"][0]["url"])
    print(res)