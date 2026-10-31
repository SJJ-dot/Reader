import re
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup
from requests.adapters import HTTPAdapter
from urllib3.util import Retry
from log import log

# 书海阁：https://book.washai.net/
# 站点对 JSON 接口（searchByPage / queryBookDetail / queryIndexList / queryReadPage）
# 启用了 Cloudflare Bot Management，普通 HTTP 客户端（requests / curl）会被拦截返回 403。
# 但站点正文为服务端渲染（SSR），搜索结果页 / 书籍详情页 / 目录页 / 章节页均可直接抓取，
# 因此本源改为解析 SSR HTML，绕开被拦截的 JSON 接口，普通 requests 即可稳定工作。

BASE = "https://book.washai.net"

# 全局 Session：连接复用 + 自动重试（网络波动 / 超时 / 5xx）
session = requests.Session()
retries_strategy = Retry(
    total=3,
    backoff_factor=1,
    status_forcelist=[500, 502, 503, 504],
    raise_on_status=False,
)
adapter = HTTPAdapter(max_retries=retries_strategy)
session.mount("http://", adapter)
session.mount("https://", adapter)
session.headers.update({
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
})

# 目录分页安全阀，避免异常时死循环
_MAX_PAGE = 200


def getSiteUrl():
    return BASE


def _get_html(url, params=None):
    resp = session.get(url, params=params, timeout=(10, 30))
    resp.encoding = "utf-8"
    if resp.status_code != 200:
        raise Exception(f"HTTP {resp.status_code} 获取页面失败: {url}")
    return resp.text


def _get_book_id(url):
    """从 /book/{bookId}.html 或 /book/{bookId}/{indexId}.html 中提取 bookId"""
    m = re.search(r"/book/(\d+)(?:\.html|/)", str(url))
    if not m:
        raise Exception(f"无法从链接解析书籍 ID: {url}")
    return m.group(1)


def _chapter_index_id(url):
    """提取章节链接中的章节 ID（/book/{bookId}/{indexId}.html）"""
    m = re.search(r"/book/\d+/(\d+)(?:\.html)?", str(url))
    return m.group(1) if m else str(url).strip().rstrip("/")


def _clean_title(title):
    """清理章节标题里附带的标签，如 [免费] / [VIP]"""
    return re.sub(r"\s*\[[^\]]*\]\s*$", "", str(title or "").strip())


def search(query):
    """搜索书籍，返回 [{bookTitle, bookAuthor, bookUrl}]"""
    html = _get_html(f"{BASE}/book/bookclass.html", {"k": query})
    soup = BeautifulSoup(html, "html.parser")

    books = []
    seen = set()
    for card in soup.select("article.classBookCard"):
        a = card.select_one("h3.classBookTitle a")
        if not a:
            continue
        href = a.get("href", "")
        if not re.match(r"^/book/\d+\.html$", href) or href in seen:
            continue
        title = a.get_text(strip=True)
        if not title:
            continue

        author = ""
        for p in card.find_all("p"):
            m = re.search(r"作者[：:]\s*([^\s|·]+)", p.get_text(" ", strip=True))
            if m:
                author = m.group(1).strip()
                break

        seen.add(href)
        books.append({
            "bookTitle": title,
            "bookAuthor": author,
            "bookUrl": urljoin(BASE, href),
        })

    return books


def _parse_chapters_from_html(html, book_id):
    """从目录页 HTML 中解析章节列表"""
    soup = BeautifulSoup(html, "html.parser")
    chapters = []
    for a in soup.find_all("a", href=re.compile(rf"^/book/{book_id}/\d+\.html$")):
        title = _clean_title(a.get_text(strip=True))
        href = a.get("href", "")
        if not title or not href:
            continue
        chapters.append({
            "title": title,
            "url": urljoin(BASE, href),
        })
    return chapters


def _get_all_chapters(book_id):
    """全量抓取目录：/book/indexList-{id}.html 为第 1 页，后续为 /book/indexList/{id}/{n}.html

    末页之后继续翻页会返回 404，遇到请求失败或本页无新增章节即停止。
    """
    chapters = []
    seen_ids = set()
    curr = 1
    while curr <= _MAX_PAGE:
        if curr == 1:
            url = f"{BASE}/book/indexList-{book_id}.html"
        else:
            url = f"{BASE}/book/indexList/{book_id}/{curr}.html"

        try:
            html = _get_html(url)
        except Exception as e:
            log(f"书海阁 目录第 {curr} 页获取失败，停止翻页: {e}")
            break

        page = _parse_chapters_from_html(html, book_id)
        if not page:
            break

        added = 0
        for ch in page:
            cid = _chapter_index_id(ch["url"])
            if cid in seen_ids:
                continue
            seen_ids.add(cid)
            chapters.append(ch)
            added += 1

        # 本页没有新增章节，说明已到末页
        if added == 0:
            break
        curr += 1

    return chapters


def _get_latest_chapters(book_id, last_chapter_url):
    """增量更新：返回本地最新章节之后的新增章节（旧->新排序）"""
    anchor = _chapter_index_id(last_chapter_url)
    all_chapters = _get_all_chapters(book_id)
    result = []
    found = False
    for ch in all_chapters:
        if _chapter_index_id(ch["url"]) == anchor:
            found = True
            continue
        if found:
            result.append(ch)
    # 本地记录可能已失效（未找到锚点），返回空列表表示无更新
    return result


def getDetails(url, lastChapterUrl=None):
    """获取书籍详情 + 目录

    :param lastChapterUrl: 本地已缓存的最新章节链接（可选）。
        传入时执行更新检查，仅返回本地最新章节之后的新增章节。
    """
    book_id = _get_book_id(url)
    html = _get_html(f"{BASE}/book/{book_id}.html")
    soup = BeautifulSoup(html, "html.parser")

    title = ""
    h1 = soup.find("h1")
    if h1:
        title = h1.get_text(strip=True)
    if not title:
        og = soup.find("meta", property="og:title")
        if og and og.get("content"):
            # 《书名》-作者-最新章节在线阅读-书海阁
            title = og["content"].strip("《》").split("-")[0].strip()

    author = ""
    og = soup.find("meta", property="og:title")
    if og and og.get("content"):
        parts = [p.strip() for p in og["content"].split("-")]
        if len(parts) >= 2:
            author = parts[1]

    cover = ""
    og_img = soup.find("meta", property="og:image")
    if og_img and og_img.get("content"):
        cover = urljoin(BASE, og_img["content"])

    intro = ""
    og_desc = soup.find("meta", property="og:description")
    if og_desc and og_desc.get("content"):
        intro = og_desc["content"].strip()

    info = {
        "url": urljoin(BASE, f"/book/{book_id}.html"),
        "title": title,
        "author": author,
        "intro": intro,
        "cover": cover,
    }

    if not lastChapterUrl:
        return {**info, "chapterList": _get_all_chapters(book_id)}

    return {**info, "chapterList": _get_latest_chapters(book_id, lastChapterUrl)}


def getChapterContent(url):
    """获取章节正文（HTML 格式）"""
    html = _get_html(url)
    soup = BeautifulSoup(html, "html.parser")

    panel = soup.select_one("#readPanel") or soup.select_one("#showReading")
    if not panel:
        return ""
    body = panel.select_one(".readChapterAppendBody")
    if not body:
        return ""

    paragraphs = []
    for p in body.find_all("p"):
        text = p.get_text().strip()
        if not text:
            continue
        paragraphs.append(f"<p>{text}</p>")
    return "\n".join(paragraphs)


if __name__ == '__main__':
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
