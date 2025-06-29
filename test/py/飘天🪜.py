from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

from log import log


def isSupported(url):
    if "piaotia.com" in url:
        return True
    return False


def search(query):
    url = "https://www.piaotia.com/modules/article/search.php"
    data = {"searchkey": query.encode('gbk'), "searchtype": "articlename"}
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    }
    response = requests.post(url, data=data, headers=headers, timeout=(5, 10))
    response.encoding = 'gbk'
    log(response.request)
    log(response)
    soup = BeautifulSoup(response.text, 'html.parser')

    results = []
    if "bookinfo" in response.request.url:
        results.append({
            "bookTitle": soup.select_one("#content h1").text.strip(),
            "bookUrl": response.request.url,
            "bookAuthor": soup.select("td")[5].text.split("者：")[1].strip()
        })
    else:
        book_list = soup.select("tr")[1:]  # Skip the header row
        for book_element in book_list:
            results.append({
                "bookTitle": book_element.select_one("a").text.strip(),
                "bookUrl": urljoin(url, book_element.select_one("a").get("href")),
                "bookAuthor": book_element.select("td")[2].text.strip()
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
        "title": soup.select_one("#content h1").text.strip(),
        "author": soup.select("td")[5].text.split("者：")[1].strip(),
        "intro": "内容简介：",
        "chapterList": []
    }
    span_element = soup.find('span', string="内容简介：")
    if span_element:
        siblings = span_element.find_next_siblings(string=True)
        for sibling in siblings:
            if sibling.strip():
                book["intro"] += "\n"
                book["intro"] += sibling.strip()
                break
    # https://www.piaotia.com/bookinfo/15/15621.html
    # https://www.piaotia.com/html/15/15621/

    response = requests.get(url.replace("bookinfo", "html").replace(".html", "/"), headers=headers, timeout=(5, 10))
    response.encoding = 'gbk'
    html = response.text
    log(response)
    soup = BeautifulSoup(html, 'html.parser')
    chapter_list_el = soup.select(".centent a")
    for chapter_el in chapter_list_el:
        chapter = {
            "title": chapter_el.text.strip(),
            "url": urljoin(response.request.url, chapter_el.get("href"))
        }
        book["chapterList"].append(chapter)

    return book


def getChapterContent(url):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    }
    response = requests.get(url, headers=headers, timeout=(5, 10))
    response.encoding = 'gbk'
    log(response)
    html = response.text
    soup = BeautifulSoup(html, 'html.parser')
    content = soup.find_all('br')[1].prettify()
    content = content.split("<!--")[0]
    return content


