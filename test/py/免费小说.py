import re
import time
import re
from urllib.parse import urljoin
import requests
from requests.adapters import HTTPAdapter
from urllib3.util import Retry
from bs4 import BeautifulSoup, NavigableString
from log import log

# 1. 初始化一个全局的 Session 对象，实现连接复用与 TCP 握手共享
session = requests.Session()

# 2. 配置底层自动重试机制
retries_strategy = Retry(
    total=3,
    backoff_factor=1,
    status_forcelist=[500, 502, 503, 504],
    raise_on_status=False
)
adapter = HTTPAdapter(max_retries=retries_strategy)
session.mount("http://", adapter)
session.mount("https://", adapter)

session.headers.update({
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
})


def _get(url, headers=None, timeout=(10, 30)):
    try:
        return session.get(url, headers=headers, timeout=timeout)
    except requests.exceptions.RequestException as e:
        log.error(f"Request failed for {url}: {e}")
        raise


def getSiteUrl():
    return "https://cn.zhys.tw"


def search(query):
    # GET /search?q=keyword
    response = session.get(
        urljoin(getSiteUrl(), "/search"),
        params={"q": query},
        timeout=(10, 30)
    )
    response.encoding = "utf-8"
    soup = BeautifulSoup(response.text, "html.parser")
    books = []

    # 搜索结果在 ul.space-y-4 > li[itemprop='mainEntity'] > a[itemprop='url']
    # 书名在 a 内的 h2[itemprop='name']
    for li in soup.select("ul.space-y-4 li[itemprop='mainEntity']"):
        a = li.select_one("a[itemprop='url']")
        if a:
            href = a.get("href")
            title_el = a.select_one("h2[itemprop='name']")
            title = title_el.get_text(strip=True) if title_el else ""
            if title and href:
                books.append({
                    "bookTitle": title,
                    "bookUrl": urljoin(getSiteUrl(), href),
                })

    return books


def getDetails(url):
    response = _get(url)
    response.encoding = "utf-8"
    soup = BeautifulSoup(response.text, "html.parser")

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

    # 章节列表在 a[href*='/read/']
    chapter_links = soup.select("a[href*='/read/']")
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
        if title:
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
    response = _get(url)
    response.encoding = "utf-8"
    soup = BeautifulSoup(response.text, "html.parser")

    # 正文在 #article-content（class reader-text）
    con = soup.select_one("#article-content")
    if not con:
        con = soup.select_one(".reader-text")
    if not con:
        con = soup.select_one("article")

    if not con:
        return ""

    return con.prettify()


if __name__ == '__main__':
    res = search("我的")
    print(res)

    print("================================")
    print("================================")
    print("================================")
    res = getDetails(res[0]["bookUrl"])
    print(res)

    print("================================")
    print("================================")
    print("================================")
    res = getChapterContent(res["chapterList"][0]["url"])
    print(res)