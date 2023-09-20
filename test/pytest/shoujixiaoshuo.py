from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup
from log import log

# {
#   "bookSourceComment": "",
#   "bookSourceGroup": "",
#   "bookSourceName": "手机小说",
#   "bookSourceType": 0,
#   "bookSourceUrl": "https://www.shoujixs.net",
#   "bookUrlPattern": "",
#   "concurrentRate": "",
#   "customOrder": 31,
#   "enabled": true,
#   "enabledCookieJar": false,
#   "enabledExplore": false,
#   "exploreUrl": "",
#   "header": "",
#   "lastUpdateTime": 1675926069852,
#   "loginCheckJs": "",
#   "loginUi": "",
#   "loginUrl": "",
#   "respondTime": 180000,
#   "ruleBookInfo": {
#     "author": "//meta[@property='og:novel:author']/@content",
#     "coverUrl": "//meta[@property='og:image']/@content",
#     "downloadUrls": "id.shojixsinto.0@tag.a.0@src",
#     "intro": "//meta[@property='og:description']/@content",
#     "kind": "//meta[@property='og:novel:category']/@content",
#     "lastChapter": "//meta[@property='og:novel:latest_chapter_name']/@content",
#     "name": "//meta[@property='og:novel:book_name']/@content"
#   },
#   "ruleContent": {
#     "content": "id.zjny.0@html",
#     "imageStyle": "0"
#   },
#   "ruleExplore": {},
#   "ruleReview": {},
#   "ruleSearch": {
#     "author": "class.c_value.0@text",
#     "bookList": "class.c_row",
#     "bookUrl": "class.c_subject.0@tag.a.0@href",
#     "coverUrl": "tag.img.0@src",
#     "intro": "class.c_description.0@text",
#     "kind": "class.c_value.1@text",
#     "lastChapter": "class.c_tag.1@class.c_value.0@tag.a.0@text",
#     "name": "class.c_subject.0@tag.a.0@text",
#     "wordCount": "class.c_value.2@text"
#   },
#   "ruleToc": {
#     "chapterList": "//div[@id=\"lbks\"]/dl/dt[last()]/following-sibling::dd/a",
#     "chapterName": "text",
#     "chapterUrl": "href",
#     "nextTocUrl": "class.right.0@tag.a.0@href"
#   },
#   "searchUrl": "/modules/article/search.php,{'charset':'gbk','body':'searchkey={{key}}','method':'POST'}",
#   "weight": 0
# }
def search(query):
    """
    书源搜索函数
    :param query: 搜索关键词
    :return [{"bookTitle":"书名", "bookUrl": "书籍链接", "bookAuthor": "作者"}, ...] 搜索结果列表
    """
    # POST请求的URL
    url = 'https://www.shoujixs.net/modules/article/search.php'

    # POST请求的数据
    data = {'searchkey': query.encode('gbk')}

    # 发送POST请求
    response = requests.post(url, data=data)
    response.encoding = 'gbk'
    # 创建BeautifulSoup对象
    soup = BeautifulSoup(response.text, 'html.parser')

    # 找到所有的链接
    links = soup.find_all('a')

    # 打印所有链接的文本和URL
    for link in links:
        full_url = urljoin(url, link.get('href'))
        log(f"{link.text} {full_url}")


def getDetails(book_url):
    """
    获取章节列表
    :param book_url: 书籍链接
    :return
    {"url": "书籍链接", "title": "书名", "author": "作者", "intro": "简介", "cover": "封面链接",
     "chapterList": [{"title": "章节名", "URL": "章节链接"}, ...]
     }
    """
    pass


def getChapterContent():
    """
    获取章节内容
    :param chapter_url: 章节链接
    :return: 章节内容 html格式
    """
    pass


if __name__ == '__main__':
    search("金刚不坏大寨主")
