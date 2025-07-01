from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

from SessionManager import start_verification_activity, get_cookie
from log import log


def isSupported(url):
    if "69shuba.com" in url:
        return True
    return False


def search(query):
    url = f"https://www.69shuba.com/modules/article/search.php"
    data = {"searchkey": query.encode('gbk'), "searchtype": "all"}
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    }

    html_text = ""
    cookies = get_cookie(url)
    if "cf_clearance" in cookies:
        headers["Cookie"] = cookies

        response = requests.post(url, data=data, headers=headers, timeout=(5, 10))
        response.encoding = 'gbk'
        log(response.request)
        log(response)
        html_text = response.text
        if response.status_code == 403:
            log("403 Forbidden")
            return []
    else:
        # 测试是否可以连通
        response = requests.get(url, headers=headers, timeout=(5, 10))
        response.encoding = 'gbk'
        if response.status_code == 403:
            log("403 Forbidden")
            return []

    if "cf_clearance" not in cookies or "newbox" not in html_text:
        cookies = start_verification_activity(url)
        headers["Cookie"] = cookies
        response = requests.post(url, data=data, headers=headers, timeout=(5, 10))
        response.encoding = 'gbk'
        log(response.request)
        log(response)
        html_text = response.text

    soup = BeautifulSoup(html_text, 'html.parser')
    book_list = soup.select(".newbox li")
    results = []

    for book_element in book_list:
        results.append({
            "bookTitle": book_element.select_one("h3").text.strip(),
            "bookUrl": urljoin(url, book_element.select_one(".imgbox").get("href")),
            "bookAuthor": book_element.select_one("label").text.strip()
        })

    return results


def getDetails(url):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    }
    response = requests.get(url, headers=headers, timeout=(5, 10))
    response.encoding = 'gbk'
    html = response.text
    log(response.request)
    log(response)
    soup = BeautifulSoup(html, 'html.parser')
    book = {
        "url": url,
        "title": soup.select("meta[property='og:novel:book_name']")[0].get("content"),
        "author": soup.select("meta[property='og:novel:author']")[0].get("content"),
        "intro": soup.select("meta[property='og:description']")[0].get("content"),
        "cover": soup.select("meta[property='og:image']")[0].get("content"),
        "chapterList": []
    }

    get_chapter_list(url, book["chapterList"])

    return book


def get_chapter_list(url, chapter_list):
    url = url.replace(".htm", "/")
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    }
    response = requests.get(url, headers=headers, timeout=(5, 10))
    response.encoding = 'gbk'
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    chapter_list_el = soup.select("#catalog a")
    for i, chapter_el in enumerate(chapter_list_el):
        if i == 0:
            continue
        chapter = {
            "title": chapter_el.text.strip(),
            "url": urljoin(url, chapter_el.get("href"))
        }
        chapter_list.insert(0, chapter)


def getChapterContent(url):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    }
    response = requests.get(url, headers=headers, timeout=(5, 10))
    response.encoding = 'gbk'
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    txt_el = soup.select_one(".txtnav")
    # 删除h1
    for h1 in txt_el.find_all("h1"):
        h1.decompose()
    for h1 in txt_el.find_all("div"):
        h1.decompose()

    return txt_el.prettify()


