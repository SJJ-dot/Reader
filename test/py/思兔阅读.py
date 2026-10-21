from urllib.parse import urljoin, quote

import requests
from bs4 import BeautifulSoup

_session = requests.Session()
_session.trust_env = False


def getSiteUrl():
    return "https://www.sto66.com"

def search(query):
    url = "https://www.sto66.com/search/" + quote(query)+".html"
    response = _session.get(url, timeout=(5, 10))
    response.encoding = 'utf-8'
    soup = BeautifulSoup(response.text, 'html.parser')

    results = []
    book_list = soup.select(".bookbox")
    for book_element in book_list:
        results.append({
            "bookTitle": book_element.select("a")[0].text.strip(),
            "bookUrl": urljoin(url, book_element.select("a")[0].get("href")),
            "bookAuthor": book_element.select(".author")[0].text.replace("作者：", "").strip()
        })

    return results


def getDetails(url):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    }
    response = _session.get(url, headers=headers, timeout=(5, 10))
    response.encoding = 'uft-8'
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    book = {
        "url": url,
        "title": soup.select_one(".booktitle").text.strip(),
        "author": soup.select_one(".booktag a").text.strip(),
        "intro": soup.select_one(".bookintro").text.strip(),
        "chapterList": []
    }

    chapter_url = soup.select("#allchapter a")[3].get("href")
    getChapterList(urljoin(url, chapter_url), book["chapterList"])

    return book

def getChapterList(url, chapter_list):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    }
    response = _session.get(url, headers=headers, timeout=(5, 10))
    response.encoding = 'uft-8'
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    chapter_elements = soup.select(".chapterlist a")
    for chapter_element in chapter_elements:
        chapter_list.append({
            "title": chapter_element.text.strip(),
            "url": urljoin(url, chapter_element.get("href"))
        })

    next_element = soup.select_one("#linkNext")
    if next_element and next_element.text.strip() == "下一页":
        next_page_url = next_element.get("href")
        getChapterList(urljoin(url, next_page_url), chapter_list)


def getChapterContent(url):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    }
    response = _session.get(url, headers=headers, timeout=(5, 10))
    response.encoding = 'utf-8'
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    con = soup.select_one("#content")
    return con.prettify()

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