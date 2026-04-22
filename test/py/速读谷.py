from urllib.parse import urljoin, quote, urlparse

import requests
from bs4 import BeautifulSoup

def getSiteUrl():
    return "https://www.sudugu.org"

def search(query):
    url = "https://www.sudugu.org/i/sor.aspx?key=" + quote(query)
    response = requests.get(url, timeout=(5, 10))
    response.encoding = 'utf-8'
    soup = BeautifulSoup(response.text, 'html.parser')

    results = []
    book_list = soup.select(".itemtxt")
    for book_element in book_list:
        results.append({
            "bookTitle": book_element.select("a")[0].text.strip(),
            "bookUrl": urljoin(url, book_element.select("a")[0].get("href")),
            "bookAuthor": book_element.select("a")[1].text.replace("作者：", "").strip()
        })

    return results


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
        "title": soup.select_one(".itemtxt a").text.strip(),
        "author": soup.select(".itemtxt a")[1].text.replace("作者：", "").strip(),
        "intro": soup.select_one(".des").text.strip(),
        "chapterList": []
    }
    getChapterList(soup, url, book["chapterList"])

    return book


def getChapterList(soup, url, chapter_list):
    chapter_elements = soup.select("#list li a")
    for chapter_element in chapter_elements:
        chapter_list.append({
            "title": chapter_element.text.strip(),
            "url": urljoin(url, chapter_element.get("href"))
        })

    next_element = soup.select("#pages a")
    if next_element and len(next_element) > 0 and next_element[-1].text.strip() == "下一页":
        next_page_url = urljoin(url, next_element[-1].get("href"))
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
        }
        response = requests.get(next_page_url, headers=headers, timeout=(5, 10))
        response.encoding = 'uft-8'
        html = response.text
        soup = BeautifulSoup(html, 'html.parser')
        getChapterList(soup, next_page_url, chapter_list)


def getChapterContent(url):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    }
    response = requests.get(url, headers=headers, timeout=(5, 10))
    response.encoding = 'utf-8'
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    con = soup.select_one(".con")
    return con.prettify()
