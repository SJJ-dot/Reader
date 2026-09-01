import html
import json
import re
from urllib.parse import urljoin

import requests
from requests.adapters import HTTPAdapter
from urllib3.util import Retry
from log import log

# 书海阁：https://book.feius.live/
# 页面内容全部由前端 AJAX 渲染，因此直接调用站点自身的 JSON 接口：
#   搜索   GET /book/searchByPage?curr=&limit=&keyword=
#   详情   GET /book/queryBookDetail/{bookId}
#   目录   GET /book/queryIndexList?bookId=&curr=&limit=&orderBy=index_num asc
#   正文   GET /book/queryReadPage?bookId=&bookIndexId=

# 1. 初始化全局 Session，实现连接复用与 TCP 握手共享
session = requests.Session()

# 2. 配置底层自动重试（网络波动 / 超时 / 5xx）
retries_strategy = Retry(
    total=3,
    backoff_factor=1,
    status_forcelist=[500, 502, 503, 504],
    raise_on_status=False
)
adapter = HTTPAdapter(max_retries=retries_strategy)
session.mount("http://", adapter)
session.mount("https://", adapter)

# 统一配置全局 headers
session.headers.update({
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36",
    "Accept": "application/json, text/plain, */*",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    "Referer": "https://book.feius.live/",
})

# 站点接口单页上限（实测 limit 传再大也会被服务端压回 100）
_PAGE_LIMIT = 100
# 目录抓取安全阀，避免接口异常时死循环
_MAX_PAGE = 200


def getSiteUrl():
    return "https://book.feius.live"


def _request(url, params=None):
    """requests 优先，失败则回退到 web_get / 人机验证（WebView 渲染，可过 JS 挑战）"""
    try:
        return _request_by_session(url, params)
    except Exception as e:
        log(f"书海阁 request failed: {e}")
        return _request_by_webview(url, params)


def _request_by_session(url, params=None):
    response = session.get(url, params=params, timeout=(10, 30))
    response.encoding = "utf-8"
    return _parse_json(response.text)


def _request_by_webview(url, params=None):
    # 延迟导入：zeroconf / WebView 相关依赖只在降级兜底时才需要
    from urllib.parse import urlencode

    from SessionManager import verification_activity_get
    from WebViewClient import web_get

    full_url = url
    if params:
        full_url = f"{url}?{urlencode(params)}"

    def parse(result):
        result = json.loads(result)
        html_text = result.get("html", "")
        if "https://challenges.cloudflare.com" in html_text:
            raise Exception("人机验证")
        if "此验证用于防止机器人自动化访问" in html_text:
            raise Exception("人机验证")
        return _parse_json(html_text)

    try:
        return parse(web_get(full_url))
    except Exception as e:
        log(f"书海阁 web_get failed: {e}")
        return parse(verification_activity_get(full_url))


def _parse_json(text):
    """从响应文本中提取 JSON（兼容 WebView 返回的 <pre> 包裹内容）"""
    text = (text or "").strip()
    try:
        data = json.loads(text)
    except ValueError:
        start = text.find("{")
        end = text.rfind("}")
        if start < 0 or end <= start:
            raise Exception("响应不是 JSON，可能触发了人机验证")
        # WebView 渲染 JSON 时会做 HTML 转义
        data = json.loads(html.unescape(text[start:end + 1]))

    if not isinstance(data, dict) or data.get("code") != 200:
        raise Exception(f"接口返回异常: {data.get('msg') if isinstance(data, dict) else 'unknown'}")
    return data.get("data") or {}


def _getBookId(url):
    """从 /book/{bookId}.html 或 /book/{bookId}/{indexId}.html 中提取 bookId"""
    m = re.search(r"/book/(\d+)(?:\.html|/)", str(url))
    if not m:
        raise Exception(f"无法从链接中解析书籍 ID: {url}")
    return m.group(1)


def _api(path, params=None):
    return _request(urljoin(getSiteUrl(), path), params)


def search(query):
    """搜索书籍，返回 [{bookTitle, bookAuthor, bookUrl}]"""
    data = _api("/book/searchByPage", {
        "curr": 1,
        "limit": _PAGE_LIMIT,
        "keyword": query,
    })

    books = []
    for item in data.get("list") or []:
        book_id = str(item.get("id") or "").strip()
        title = str(item.get("bookName") or "").strip()
        if not book_id or not title:
            continue
        books.append({
            "bookTitle": title,
            "bookAuthor": str(item.get("authorName") or "").strip(),
            "bookUrl": urljoin(getSiteUrl(), f"/book/{book_id}.html"),
        })

    return books


def getDetails(url):
    """获取书籍详情 + 完整目录"""
    book_id = _getBookId(url)

    detail = _api(f"/book/queryBookDetail/{book_id}")

    book = {
        "url": urljoin(getSiteUrl(), f"/book/{book_id}.html"),
        "title": str(detail.get("bookName") or "").strip(),
        "author": str(detail.get("authorName") or "").strip(),
        "intro": str(detail.get("bookDesc") or "").strip(),
        "cover": urljoin(getSiteUrl(), str(detail.get("picUrl") or "").strip()),
        "chapterList": []
    }

    # 目录分页抓取，每页 100 章，按章节序号正序
    chapters = []
    total = 0
    curr = 1
    while curr <= _MAX_PAGE:
        data = _api("/book/queryIndexList", {
            "bookId": book_id,
            "curr": curr,
            "limit": _PAGE_LIMIT,
            "orderBy": "index_num asc",
        })
        total = int(data.get("total") or 0)
        items = data.get("list") or []
        if not items:
            break
        for item in items:
            index_id = str(item.get("id") or "").strip()
            title = str(item.get("indexName") or "").strip()
            if not index_id or not title:
                continue
            chapters.append({
                "title": title,
                "url": urljoin(getSiteUrl(), f"/book/{book_id}/{index_id}.html"),
            })
        if total and len(chapters) >= total:
            break
        curr += 1

    book["chapterList"] = chapters
    return book


def _contentToHtml(content, title=None):
    """正文是纯文本（\\n 分行），统一转成 <p> 结构的 HTML"""
    lines = [line.strip() for line in str(content or "").split("\n")]
    # 正文首行通常是重复的章节名，去掉避免与阅读器自带的标题重复
    if title and lines:
        for i, line in enumerate(lines):
            if not line:
                continue
            if line == str(title).strip():
                lines[i] = ""
            break

    paragraphs = []
    for line in lines:
        if not line:
            continue
        # 纯分隔符行转成 <hr/>
        if set(line) == {"-"}:
            paragraphs.append("<hr/>")
        else:
            paragraphs.append(f"<p>{html.escape(line)}</p>")
    return "\n".join(paragraphs)


def getChapterContent(url):
    """获取章节正文（HTML 格式）"""
    m = re.search(r"/book/(\d+)/(\d+)\.html", str(url))
    if not m:
        raise Exception(f"无法从链接中解析章节 ID: {url}")
    book_id, index_id = m.group(1), m.group(2)

    data = _api("/book/queryReadPage", {
        "bookId": book_id,
        "bookIndexId": index_id,
    })

    # VIP 章节未订阅时正文为空
    if data.get("needBuy"):
        return ""

    index_name = (data.get("bookIndex") or {}).get("indexName")
    return _contentToHtml(data.get("content"), index_name)


if __name__ == '__main__':
    res = search("我的")
    for book in res:
        print(book)

    print("================================")
    print("================================")
    print("================================")
    res = getDetails(res[0]["bookUrl"])
    print("url:", res["url"])
    print("title:", res["title"])
    print("author:", res["author"])
    print("intro:", res["intro"])
    print("cover:", res["cover"])
    print("chapter count:", len(res["chapterList"]))
    for c in res["chapterList"][:10]:
        print("chapter:", c["title"], c["url"])

    print("================================")
    print("================================")
    print("================================")
    res = getChapterContent(res["chapterList"][0]["url"])
    print(res)
