import re
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

_UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
       "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
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


def _book_id(url):
    match = re.search(r"/(?:xiaoshuo_|read/)(\d+)", url or "")
    return match.group(1) if match else ""


def verify(query):
    res = getDetails("https://www.x33yq.org/read/103619/")
    res = getChapterContent(res["chapterList"][0]["url"])
    return 0 if len(res) > 10 else 1


def getSiteUrl():
    return "https://www.x33yq.org"


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

    # 先访问首页拿会话，再提交搜索
    _session.get(getSiteUrl(), timeout=30)
    response = _session.post(
        urljoin(getSiteUrl(), "/search.html"),
        data={"searchkey": query},
        timeout=30,
    )
    response.encoding = "utf-8"
    soup = BeautifulSoup(response.text, "html.parser")

    books = []
    seen = set()
    for card in soup.select("div.info"):
        text = card.get_text(" ", strip=True)
        match = re.search(r"《\s*(.*?)\s*》\s*作者[:：]\s*(\S+)", text)
        if not match:
            continue
        title = match.group(1).strip()
        author = match.group(2).strip()
        link = card.select_one("a[href*='/xiaoshuo_']")
        if not link:
            continue
        book_url = urljoin(getSiteUrl(), link.get("href"))
        if book_url in seen:
            continue
        seen.add(book_url)
        books.append({
            "bookTitle": title,
            "bookUrl": book_url,
            "bookAuthor": author,
        })

    return books


def getDetails(url):
    book_id = _book_id(url)
    page_url = f"{getSiteUrl()}/read/{book_id}/"
    soup = _get(page_url)

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
    for a in soup.select("a[href]"):
        title = a.get_text(strip=True)
        no = _chapter_no(title)
        if no is None:
            continue
        links.append((no, title, urljoin(page_url, a.get("href"))))

    # 顶部有最新章节倒序块，从第一个“第一章”开始截取
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


def getChapterContent(url, _depth=0):
    # m 站正文容器不同，统一走 www
    url = (url or "").replace("://m.x33yq.org", "://www.x33yq.org")
    if _depth > 5:
        return ""

    soup = _get(url)
    content = soup.select_one("#content")
    if not content:
        return ""

    # 先找同章的“下一页”，后面会删掉导航段
    base = url[:-5] if url.endswith(".html") else url
    next_url = ""
    for a in soup.select("a[href]"):
        if a.get_text(strip=True) != "下一页":
            continue
        full = urljoin(url, a.get("href"))
        if full.startswith(base + "_"):
            next_url = full
            break

    # 去掉“请点击下一页”这种污染段落
    for p in content.select("p"):
        if "请点击下一页" in p.get_text(strip=True):
            p.decompose()

    # 去掉末尾导航段
    paragraphs = content.select("p")
    if paragraphs:
        last_text = paragraphs[-1].get_text(strip=True)
        if any(word in last_text for word in ("下一页", "章节目录", "加入书签", "返回书架", "投推荐票")):
            paragraphs[-1].decompose()

    html = content.prettify()
    if next_url:
        html += getChapterContent(next_url, _depth + 1)

    return html

if __name__ == '__main__':
    # 本地调试：直接运行 python 顶点小说.py
    keyword = "我的"
    print("========== 搜索 ==========")
    results = search(keyword)
    print(f"搜索「{keyword}」: {len(results)} 条")
    for item in results[:3]:
        print("  ", item)

    # 搜索无结果时回退到已知测试书
    book_url = results[0]["bookUrl"]

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
        print(content[-200:])
