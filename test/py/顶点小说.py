import json
from urllib.parse import quote, urljoin

from bs4 import BeautifulSoup

from SessionManager import verification_activity_get
from WebViewClient import web_get
from log import log

# 章节正文最多翻多少页，防止异常链接导致死循环
_MAX_PAGES = 10

# 命中这些特征说明触发了人机验证，需要交给前台 WebView
_VERIFY_MARKERS = (
    "challenges.cloudflare.com",
    "此验证用于防止机器人自动化访问",
    "Just a moment",
)


def _is_verify(html):
    return any(marker in html for marker in _VERIFY_MARKERS)


def _fetch(url):
    """直接走 WebView；后台 WebView 被拦截时转前台人工验证，返回 {url, html} 的 JSON"""
    def parse(result):
        result = json.loads(result)
        result["html"] = result.get("html", "")
        if _is_verify(result["html"]):
            raise Exception("人机验证")
        return result

    try:
        return json.dumps(parse(web_get(url)), ensure_ascii=False)
    except Exception as e:
        log(f"顶点小说 web_get 失败，转人工验证: {e}")
        return json.dumps(parse(verification_activity_get(url)), ensure_ascii=False)


def getSiteUrl():
    return "https://m.terry-haass.com"


def search(query):
    def parseResult(result):
        result = json.loads(result)
        soup = BeautifulSoup(result.get("html"), "html.parser")

        books = []
        for bookEl in soup.select(".sone"):
            links = bookEl.select("a")
            if len(links) < 2:
                continue
            href = links[0].get("href") or ""
            bookUrl = f"https://m.terry-haass.com/{href.split('/')[1]}/"
            books.append({
                "bookTitle": links[0].text.strip(),
                "bookUrl": urljoin(url, bookUrl),
                "bookAuthor": links[1].text.strip(),
            })

        return books

    url = ("https://m.terry-haass.com/modules/article/search.php?"
           f"searchkey={quote(query)}&Submit.x=20&Submit.y=16")
    return parseResult(_fetch(url))


def getDetails(url):
    def parseBookInfo(result):
        result = json.loads(result)
        soup = BeautifulSoup(result.get("html"), "html.parser")
        return {
            "url": url,
            "title": soup.select_one("meta[property='og:novel:book_name']").get("content"),
            "author": soup.select_one("meta[property='og:novel:author']").get("content"),
            "intro": soup.select_one("meta[property='og:description']").get("content"),
            "cover": soup.select_one("meta[property='og:image']").get("content"),
        }

    def parseChapterList(result):
        result = json.loads(result)
        soup = BeautifulSoup(result.get("html"), "html.parser")

        chapterList = []
        for chapter_el in soup.select(".infoad"):
            title = chapter_el.text.split("&nbsp;")[0].strip()
            href = chapter_el.get("href")
            if not title or not href:
                continue
            chapterList.append({
                "title": title,
                "url": urljoin(url, href),
            })
        return chapterList

    url = url.replace("://www.", "://m.")
    book = parseBookInfo(_fetch(url))
    book["chapterList"] = parseChapterList(_fetch(url + "/index.html"))

    return book


def _extract_content(soup, url, visited, depth):
    if url in visited or depth >= _MAX_PAGES:
        return ""
    visited.add(url)

    parts = []
    for p in soup.select("#novelcontent > p"):
        text = p.get_text(strip=True)
        if not text:
            continue
        classes = " ".join(p.get("class") or [])
        if "chapternext" in classes:
            continue
        # 过滤导航和“退出阅读模式”提示
        if any(word in text for word in ("下一页", "上一页", "转码失败", "阅读模式")):
            continue
        parts.append(text)

    html = "<p>" + "</p><p>".join(parts) + "</p>" if parts else ""

    # 只认同一章节的分页（xxx_2.html），不要外站广告里的“下一页”
    base = url[:-5] if url.endswith(".html") else url
    next_url = ""
    for a in soup.select("a[href]"):
        full = urljoin(url, a.get("href"))
        if full.startswith(base + "_") and full != url:
            next_url = full
            break

    if next_url:
        data = json.loads(_fetch(next_url))
        next_soup = BeautifulSoup(data.get("html"), "html.parser")
        html += _extract_content(next_soup, next_url, visited, depth + 1)

    return html


def getChapterContent(url):
    data = json.loads(_fetch(url))
    soup = BeautifulSoup(data.get("html"), "html.parser")
    return _extract_content(soup, data.get("url") or url, set(), 0)


if __name__ == '__main__':
    # 本地调试：直接运行 python 顶点小说.py
    keyword = "我的"
    print("========== 搜索 ==========")
    results = search(keyword)
    print(f"搜索「{keyword}」: {len(results)} 条")
    for item in results[:3]:
        print("  ", item)

    # 搜索无结果时回退到已知测试书
    book_url = results[0]["bookUrl"] if results else "https://m.terry-haass.com/1423629234254/"

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

        # 已知测试书《我的游戏致郁全球》第一章分两页，第二页包含这个标记
        if book.get("title") == "我的游戏致郁全球":
            print("第二页已加载:", "实陈轩的年龄" in content)
