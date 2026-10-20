from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

from log import log


# 支持搜索可以不需要实现verify
# def verify(query):
#     res = getDetails("https://jpxs123.top/txt/wk/")
#     res = getChapterContent(res["chapterList"][0]["url"])
#     return 0 if len(res) > 10 else 1


_session = requests.Session()
_session.trust_env = False


def getSiteUrl():
    return "https://jpxs123.top/"


def search(query):
    session = requests.Session()
    session.headers.update({
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36",
    })

    # 访问搜索页以获取必要的 Cookie
    session.get(getSiteUrl(), timeout=(5, 10))

    data = {
        "keyboard": query.encode('gbk'),
        "show": "title",
        "classid": "0"
    }
    headers = {
        "Content-Type": "application/x-www-form-urlencoded",
        "Origin": "https://jpxs123.top",
        "Referer": getSiteUrl(),
    }
    base_url = "https://jpxs123.top/e/search/indexsearch.php"
    response = session.post(base_url, data=data, headers=headers, timeout=(5, 10))
    response.encoding = 'gbk'
    soup = BeautifulSoup(response.text, 'html.parser')
    books = []

    for bookEl in soup.select(".bk"):
        a_tag = bookEl.select_one("h3 a")
        if a_tag:
            books.append({
                "bookTitle": a_tag.text.strip(),
                "bookUrl": urljoin(base_url, a_tag.attrs["href"]),
            })
    return books


def getDetails(url):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    }
    response = _session.get(url, headers=headers, timeout=(5, 10))
    response.encoding = 'gbk'
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')

    # 提取标题，去掉括号内的部分（如章节数）
    title_el = soup.select_one(".infos h1")
    title = title_el.text.split('(')[0].strip() if title_el else ""

    # 提取作者
    author_el = soup.select_one(".infos .date span a")
    author = author_el.text.strip() if author_el else ""

    # 提取简介
    intro_el = soup.select_one(".infos p")
    intro = intro_el.text.strip() if intro_el else ""

    # 提取封面
    pic_el = soup.select_one(".pic img")
    cover = urljoin(url, pic_el.get("src")) if pic_el else ""

    book = {
        "url": url,
        "title": title,
        "author": author,
        "intro": intro,
        "cover": cover,
        "chapterList": []
    }

    chapter_elements = soup.select(".book_list a")
    for chapter_element in chapter_elements:
        book["chapterList"].append({
            "title": chapter_element.text.strip(),
            "url": urljoin(url, chapter_element.get("href"))
        })

    return book


def getChapterContent(url):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    }
    response = _session.get(url, headers=headers, timeout=(5, 10))
    response.encoding = 'gbk'
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    con = soup.select_one(".read_chapterDetail")
    if not con:
        return ""

    # 移除脚本和不需要的部分
    for s in con.select("script"):
        s.decompose()

    content = con.prettify()

    # 检查是否有下一页（有些网站一章分多页）
    next_page_a = soup.find("a", string="下一页")
    if next_page_a:
        next_url = urljoin(url, next_page_a.get("href"))
        if "_" in next_url:
            content += getChapterContent(next_url)

    return content


if __name__ == '__main__':
    # 测试搜索
    search_results = search("我的")
    print(f"搜索结果数量: {len(search_results)}")
    if search_results:
        print(f"第一个搜索结果: {search_results[0]}")

    # 测试获取详情
    detail_url = "https://jpxs123.top/dsxs/12241.html"
    book_details = getDetails(detail_url)
    print(f"书籍详情: {book_details['title']} - {book_details['author']}")
    print(f"章节数量: {len(book_details['chapterList'])}")

    # 测试获取内容
    if book_details['chapterList']:
        chapter_url = book_details['chapterList'][0]['url']
        content = getChapterContent(chapter_url)
        print(f"第一章内容长度: {len(content)}")
        print(f"第一章内容开头: {content[:100]}")  # 打印前100个字符
        print(f"第一章内容结束：: {content[-100:]}")  # 打印最后100个字符
