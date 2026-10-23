"""
聚合搜索：用神马搜索（m.sm.cn）按书名检索，返回各小说站的书籍详情页。

APP 会按 bookUrl 的域名把结果归属到本地已安装的书源，
所以需要本地安装了对应站点的书源，结果才能真正打开。

说明：
- Bing 国内版对中文书名经常返回跑偏结果，且结果链接是 bing.com/ck/a 跳转，
  脚本无法直接拿到真实地址，因此改用神马搜索。
- 神马结果卡片的 data-url 指向书籍详情页（不是章节页），适合直接作为 bookUrl。
"""

import re
from urllib.parse import quote, urlparse

import requests
from bs4 import BeautifulSoup
from log import log

_UA = ("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
       "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
_HEADERS = {"User-Agent": _UA, "Accept-Language": "zh-CN,zh;q=0.9"}

# 书名里常见的通用词，清理用户输入时去掉
_QUERY_NOISE = ("全文阅读", "在线阅读", "最新章节", "小说", "txt", "TXT")

_AUTHOR_NOISE = ("完本", "连载", "精校", "无弹窗", "免费", "下载", "章节目录")

# 非书籍站点（搜索/视频/社区/新闻），避免返回无关结果
_SKIP_HOSTS = (
    "sm.cn", "baidu.com", "zhihu.com", "douban.com", "bilibili.com",
    "weibo.com", "sohu.com", "toutiao.com", "youtube.com", "google.com",
    "bing.com", "so.com", "sogou.com", "163.com", "sina.com.cn",
    "ifeng.com", "douyin.com", "kuaishou.com",
    "v.qq.com", "new.qq.com", "i.qq.com",
)

# 本地书源覆盖的站点。默认“小说书源模式”只返回这些站点的书；
# “#书源”发现模式不筛选，返回全部搜索结果。
# 新增书源后需要同步补充这里的域名。
_SUPPORTED_HOSTS = (
    "18wx.casa", "69shuba.com", "69shuba.tw", "b5200.org", "biqvge99.cc",
    "bisge.cc", "bixia11.com", "bixiange.xyz", "deqixs.org", "dzsjob.com",
    "ibiqugu.net", "jpxs123.top", "kubixs.com", "llenn.cc", "mayiwsk.info",
    "min-yuan.com", "mingzw.net", "qidian.com", "qzhd.cn", "shengxuxu.net",
    "soduzw.org", "sto66.com", "suduguu.com", "terry-haass.com", "washai.net",
    "x33yq.org", "xbiquge345.com", "xshuquge.net", "zhys.tw",
    "eexsvw.cc","zwda.com",
)


def _normalize(text):
    """去掉空格与标点，便于书名匹配"""
    return re.sub(r"[\s《》<>“”\"'·,，。.!！?？:：;；_\-—|/\\]+", "", text or "").lower()


def _clean_query(query):
    text = (query or "").strip().strip("《》").strip()
    for word in _QUERY_NOISE:
        text = text.replace(word, " ")
    return re.sub(r"\s+", " ", text).strip()


def _extract_author(title, book_title):
    """只认紧跟在书名后的括号作者，避免把“《书名(主角名)》”当成作者"""
    if not title:
        return ""
    # 标题通常以书名开头，去掉可能的前导书名号再匹配
    text = title.lstrip("《 <【[")
    pattern = re.compile(
        re.escape(book_title) + r"[^\s_《》\-|]{0,8}\s*[（(]\s*([^（()）]{1,16})\s*[)）]"
    )
    match = pattern.match(text)
    if not match:
        return ""
    author = match.group(1).strip()
    if any(word in author for word in _AUTHOR_NOISE):
        return ""
    return author


def _host_key(host):
    """m.hongxiu.com / www.hongxiu.com 视为同一站点"""
    parts = host.split(".")
    return ".".join(parts[-2:]) if len(parts) >= 2 else host


def _is_skipped(host):
    return any(host == item or host.endswith("." + item) for item in _SKIP_HOSTS)


def _fetch_page(keyword, page):
    url = f"https://m.sm.cn/s?q={quote(keyword)}"
    if page > 1:
        url += f"&page={page}"
    response = requests.get(url, headers=_HEADERS, timeout=(3, 4))
    response.encoding = "utf-8"
    return response.text


def _fetch_pages(keyword, pages=5, deadline=4.5):
    """并发抓取多页搜索结果，总耗时控制在 deadline 秒内"""
    from concurrent.futures import ThreadPoolExecutor, wait

    texts = []
    pool = ThreadPoolExecutor(max_workers=pages)
    try:
        futures = [pool.submit(_fetch_page, keyword, p) for p in range(1, pages + 1)]
        done, _ = wait(futures, timeout=deadline)
        for page, future in enumerate(futures, start=1):
            if future not in done:
                future.cancel()
                continue
            try:
                texts.append(future.result())
            except Exception as e:
                log(f"聚合搜索第 {page} 页失败: {e}")
    finally:
        pool.shutdown(wait=False, cancel_futures=True)
    return texts


_SITE_MODE_PREFIXES = ("#书源", "#站点")


def search(query):
    """
    两种模式：
    - 默认：按书名搜索，返回可归属到本地书源的书籍；
    - 以 #书源 / #站点 开头：返回收录该书的所有站点（潜在书源）。
    :param query: 书名，或 “#书源 书名”
    :return: 书籍列表 [{bookTitle, bookUrl, bookAuthor}, ...]
    """
    raw = (query or "").strip()
    site_mode = False
    for prefix in _SITE_MODE_PREFIXES:
        if raw.startswith(prefix):
            site_mode = True
            raw = raw[len(prefix):].strip()
            break

    book_title = _clean_query(raw)
    if not book_title:
        return []

    texts = _fetch_pages(book_title + " 小说")
    if not texts:
        log(f"聚合搜索「{book_title}」: 无结果")
        return []

    query_key = _normalize(book_title)
    books = []
    index = {}
    seen_sites = set()

    def add(book_url, raw_title):
        book_url = (book_url or "").strip()
        if book_url.startswith("//"):
            book_url = "https:" + book_url
        if not book_url.startswith("http"):
            return
        # 跳过“第123章”这类章节页，只要书籍页
        if re.search(r"第\s*[0-9一二三四五六七八九十百千]+\s*[章节]", raw_title or ""):
            return
        # 跳过角色/人物介绍页
        if any(word in (raw_title or "") for word in ("角色介绍", "人物介绍", "角色档案")):
            return
        # 只保留以书名开头的结果，过滤“相关推荐”和简介片段
        if not _normalize(raw_title).startswith(query_key):
            return

        # 旧域名 zwda.com 已 302 到 eexsvw.cc，统一改写以便绑定 E小说
        parsed = urlparse(book_url)
        if _host_key(parsed.netloc.lower()) == "zwda.com":
            book_url = parsed._replace(netloc="www.eexsvw.cc").geturl()

        host = urlparse(book_url).netloc.lower()
        if not host or _is_skipped(host):
            return

        # 默认模式只返回本地已有书源支持的站点；发现模式返回全部
        if not site_mode and _host_key(host) not in _SUPPORTED_HOSTS:
            return

        # 模式二：每个站点只出一条“潜在书源”结果
        if site_mode:
            site_key = _host_key(host)
            if site_key in seen_sites:
                return
            seen_sites.add(site_key)
            books.append({
                "bookTitle": f"{site_key}（潜在书源）",
                "bookAuthor": "潜在书源站点",
                "bookUrl": book_url,
            })
            return

        # 同一本书的标题卡片、简介卡片可能指向同一地址，这里去重
        dedupe_key = re.sub(r"^https?://", "", book_url).rstrip("/")
        if dedupe_key in index:
            if not index[dedupe_key]["bookAuthor"]:
                index[dedupe_key]["bookAuthor"] = _extract_author(raw_title, book_title)
            return

        # 同一站点同一本书只保留一条（红袖等站点会产出多个 SEO 变体页）
        site_key = (_host_key(host), _normalize(book_title))
        if site_key in seen_sites:
            return
        seen_sites.add(site_key)

        book = {
            "bookTitle": book_title,
            "bookUrl": book_url,
            "bookAuthor": _extract_author(raw_title, book_title),
        }
        index[dedupe_key] = book
        books.append(book)

    for text in texts:
        soup = BeautifulSoup(text, "html.parser")

        # 模板一：结果卡片，data-url 通常就是书籍详情页
        for card in soup.select("div[data-url]"):
            title_el = card.select_one(".qk-title-text")
            if title_el:
                add(card.get("data-url"), title_el.get_text(" ", strip=True))

        # 模板二：富卡片/普通结果，直接解析链接（跳过已被卡片覆盖的链接）
        for a in soup.select("a[href]"):
            if a.find_parent(attrs={"data-url": True}):
                continue
            href = a.get("href") or ""
            if not href.startswith("http") or "sm.cn" in href:
                continue
            text = a.get_text(" ", strip=True)
            if not text:
                continue
            add(href, text)

    mode = "潜在书源站点" if site_mode else "书籍"
    log(f"聚合搜索「{book_title}」({mode}): {len(books)} 条")
    return books


def _unsupported(url):
    host = urlparse(url or "").netloc or url
    raise Exception(f"小说站点不支持：{host}，请先安装该站点的书源")


def getDetails(url):
    """聚合搜索只负责搜索，详情由 bookUrl 所属站点的书源提供"""
    _unsupported(url)


def getChapterContent(url):
    """聚合搜索只负责搜索，正文由 bookUrl 所属站点的书源提供"""
    _unsupported(url)


def getSiteUrl():
    # 聚合搜索没有固定站点，返回搜索页域名用于兜底
    return "https://m.sm.cn"


if __name__ == '__main__':
    for item in search("在港综成为传说"):
        print(item)
