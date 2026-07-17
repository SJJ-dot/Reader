import time
from urllib.parse import urljoin
import requests
from requests.adapters import HTTPAdapter
from urllib3.util import Retry
from bs4 import BeautifulSoup
from log import log

# 1. 初始化一个全局的 Session 对象，实现连接复用与 TCP 握手共享
session = requests.Session()

# 2. 配置底层自动重试机制
# 针对网络波动、超时以及临时服务器错误（如 500, 502, 503, 504）自动在底层重试
retries_strategy = Retry(
    total=3,  # 最大重试次数
    backoff_factor=1,  # 重试间隔等待时间系数 (e.g. 1s, 2s, 4s...)
    status_forcelist=[500, 502, 503, 504],  # 遇到这些状态码直接重试
    raise_on_status=False  # 即使失败也返回 response，不抛出异常以维持业务逻辑
)
adapter = HTTPAdapter(max_retries=retries_strategy)
session.mount("http://", adapter)
session.mount("https://", adapter)

# 统一配置全局 headers
session.headers.update({
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
})


# 3. 封装统一的内部请求方法（完全基于全局 Session，保障连接复用）
def _get(url, headers=None, timeout=(10, 30)):
    """
    使用全局 session 发起 GET 请求。
    因为挂载了 HTTPAdapter，该方法天然自带连接池复用与底层网络重试。
    """
    try:
        # 如果传入了临时 headers，会与 session.headers 进行合并
        return session.get(url, headers=headers, timeout=timeout)
    except requests.exceptions.RequestException as e:
        log.error(f"Request failed for {url}: {e}")
        raise


def getSiteUrl():
    return "http://m.kubixs.com"


def search(query):
    base_url = "https://www.kubixs.com"
    # 先访问首页以获取 Cookie 和 CSRF _token（直接复用 session）
    response = _get(base_url)
    response.encoding = "utf-8"
    soup = BeautifulSoup(response.text, "html.parser")

    # 提取 CSRF _token
    token_input = soup.select_one("input[name='_token']")
    _token = token_input.get("value") if token_input else ""

    # 提交搜索
    data = {
        "keyword": query,
        "_token": _token,
    }
    headers = {
        "Content-Type": "application/x-www-form-urlencoded",
        "Origin": base_url,
        "Referer": base_url,
    }

    # 搜索请求也复用 session 连接池
    response = session.post(
        urljoin(base_url, "/search"),
        data=data,
        headers=headers,
        timeout=(10, 30)
    )
    response.encoding = "utf-8"
    soup = BeautifulSoup(response.text, "html.parser")
    books = []

    # 提取搜索结果
    for row in soup.select("#nr"):
        a = row.select_one("td a")
        if a:
            title = a.get_text().strip()
            href = a.get("href")
            if title and href:
                books.append({
                    "bookTitle": title,
                    "bookUrl": urljoin(base_url, href),
                })

    return books


def getDetails(url):
    url = url.replace("://m.", "://www.")

    # 1. 抓取书籍详情页
    response = _get(url)
    response.encoding = "utf-8"
    soup = BeautifulSoup(response.text, "html.parser")
    book = {
        "url": url,
        "title": soup.select("meta[property='og:novel:book_name']")[0].get("content"),
        "author": soup.select("meta[property='og:novel:author']")[0].get("content"),
        "intro": soup.select("meta[property='og:description']")[0].get("content"),
        "cover": urljoin(url, soup.select("meta[property='og:image']")[0].get("content")),
        "chapterList": []
    }

    # 2. 抓取完整目录（因同域名，此请求将 100% 复用刚才建立的 TCP 连接）
    list_url = urljoin(url, "list.html")
    response = _get(list_url)
    response.encoding = "utf-8"
    soup = BeautifulSoup(response.text, "html.parser")

    for chapter_element in soup.select("#readerlist li a"):
        title = chapter_element.get_text().strip()
        href = chapter_element.get("href")
        if title and href:
            book["chapterList"].append({
                "title": title,
                "url": urljoin(url, href)
            })

    return book


def getChapterContent(start_url):
    # 统一转换成移动端链接
    current_url = start_url.replace("https://www.", "http://m.")

    total_content = ""
    visited_urls = set()  # 避免死循环

    while current_url and current_url not in visited_urls:
        visited_urls.add(current_url)

        # 发送请求（通过 _get 内部走 Session 连接池，多页抓取时速度飞快）
        response = _get(current_url)
        response.encoding = "utf-8"
        html = response.text
        soup = BeautifulSoup(html, "html.parser")

        # 1. 提取正文内容
        con = soup.select_one(".content")
        if con:
            total_content += con.prettify()
        else:
            con_fallback = soup.select_one("#content") or soup.select_one(".txt-content")
            if con_fallback:
                total_content += con_fallback.prettify()

        # 2. 寻找“下一页”链接
        next_url = None
        for a in soup.find_all("a"):
            text = a.get_text().strip()
            if "下一页" in text or "下页" in text:
                href = a.get("href")
                if href:
                    temp_url = urljoin(current_url, href)
                    if "?page=" in temp_url:
                        next_url = temp_url
                        break

        # 3. 判断是否继续抓取下一页
        if next_url and next_url != start_url:
            current_url = next_url
        else:
            break

    return total_content


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
