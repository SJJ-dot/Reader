from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup
from log import log

def verify():
    try:
        res = getDetails("https://www.min-yuan.com/txt/wk/")
        res = getChapterContent(res["chapterList"][0]["url"])
        return len(res) > 10
    except Exception as e:
        log(f"Error :{e}")
        return False

def getSiteUrl():
    return "https://www.min-yuan.com"

def search(query):
    # 不支持搜索
    return []


def getDetails(url):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    }
    response = requests.get(url, headers=headers, timeout=(5, 10))
    response.encoding = 'uft-8'
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    book = {
        "url": url,
        "title": soup.select("meta[property='og:novel:book_name']")[0].get("content"),
        "author": soup.select("meta[property='og:novel:author']")[0].get("content"),
        "intro": soup.select("meta[property='og:description']")[0].get("content"),
        "cover": urljoin(url, soup.select("meta[property='og:image']")[0].get("content")),
        "chapterList": []
    }
    chapter_elements = soup.select("#newlist a")
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
    response = requests.get(url, headers=headers, timeout=(5, 10))
    response.encoding = 'utf-8'
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    con = soup.select_one("#booktxt")
    content = con.prettify()
    next_url = urljoin(url, soup.select("a[rel='next']")[0].get("href"))
    if "_" in next_url:
        content += getChapterContent(next_url)

    return content