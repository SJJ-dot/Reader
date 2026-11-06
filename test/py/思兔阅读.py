import json
from urllib.parse import urljoin, quote

from bs4 import BeautifulSoup

_VERIFY_MARKERS = (
    "challenges.cloudflare.com",
    "此验证用于防止机器人自动化访问",
    "Just a moment",
)


def getSiteUrl():
    return "https://www.sto66.com"


def _is_verify(html):
    return any(marker in (html or "") for marker in _VERIFY_MARKERS)


def _get(url):
    # 站点有人机验证，直接走 WebView；后台被拦时转前台人工验证
    from WebViewClient import web_get
    from SessionManager import verification_activity_get

    def parse(result):
        data = json.loads(result)
        html = data.get("html") or ""
        if _is_verify(html):
            raise Exception("人机验证")
        return html

    try:
        return parse(web_get(url, timeout=15000))
    except Exception:
        return parse(verification_activity_get(url))


def _text(soup, selector, default=""):
    el = soup.select_one(selector)
    return el.get_text(strip=True) if el else default


def search(query):
    url = "https://www.sto66.com/search/" + quote(query) + ".html"
    soup = BeautifulSoup(_get(url), "html.parser")

    results = []
    for element in soup.select(".bookbox"):
        links = element.select("a")
        if not links:
            continue
        results.append({
            "bookTitle": links[0].get_text(strip=True),
            "bookUrl": urljoin(url, links[0].get("href")),
            "bookAuthor": _text(element, ".author").replace("作者：", ""),
        })
    return results


def getDetails(url):
    soup = BeautifulSoup(_get(url), "html.parser")
    book = {
        "url": url,
        "title": _text(soup, ".booktitle"),
        "author": _text(soup, ".booktag a"),
        "intro": _text(soup, ".bookintro"),
        "chapterList": [],
    }

    all_chapters = soup.select("#allchapter a")
    catalog = next((a for a in all_chapters
                    if "查看全部章节" in a.get_text()), None)
    if catalog is None and all_chapters:
        catalog = all_chapters[-1]
    if catalog:
        getChapterList(urljoin(url, catalog.get("href")), book["chapterList"])

    return book


def getChapterList(url, chapter_list, visited=None):
    if visited is None:
        visited = set()
    if url in visited:
        return
    visited.add(url)

    soup = BeautifulSoup(_get(url), "html.parser")
    for element in soup.select(".chapterlist a"):
        chapter_list.append({
            "title": element.get_text(strip=True),
            "url": urljoin(url, element.get("href")),
        })

    next_element = soup.select_one("#linkNext")
    if next_element and next_element.get_text(strip=True) == "下一页" \
            and next_element.get("href"):
        getChapterList(urljoin(url, next_element.get("href")),
                       chapter_list, visited)


def getChapterContent(url):
    soup = BeautifulSoup(_get(url), "html.parser")
    content = soup.select_one("#content")
    return content.prettify() if content else ""


if __name__ == '__main__':
    # 本地调试：直接运行 python 思兔阅读.py
    import os
    import sys
    sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

    keyword = "我的"
    print("========== 搜索 ==========")
    results = search(keyword)
    print(f"搜索「{keyword}」: {len(results)} 条")
    for item in results[:3]:
        print("  ", item)

    # 搜索无结果时回退到已知测试书
    book_url = (results[0]["bookUrl"] if results else
                "https://www.sto66.com/book/5KPtSiJysp93mL6EMfIJrU.html")

    print("\n========== 详情 ==========")
    book = getDetails(book_url)
    chapters = book.get("chapterList") or []
    print("书名:", book.get("title"))
    print("作者:", book.get("author"))
    print("章节数:", len(chapters))
    if chapters:
        print("首章:", chapters[0])
        print("末章:", chapters[-1])

    if chapters:
        print("\n========== 正文 ==========")
        content = getChapterContent(chapters[0]["url"])
        print("正文长度:", len(content))
        print("预览:", content[:200])
