### 书源函数接口
```python
def verify():
    """
    验证书源是否可用，返回True或False
    可选，部分书籍不支持搜索功能时提供，避免验证书源失败，
    """
    pass

def getSiteUrl():
    """
    可选，如果不包含该函数，书源无法直接导入链接，也无法在书城进行导入
    :return: 书源网址，例如：https://www.69shuba.com，用于直接导入书籍详情页链接
    """
    pass

def search(query):
    """
    :param query: 搜索关键词
    :return: 书籍列表，包含以下字段：
        - bookTitle: 书籍标题
        - bookUrl: 书籍详情链接
        - bookAuthor: 书籍作者(可选)
        - bookCover: 书籍封面链接(可选)
        
    """
    pass


def getDetails(url):
    """
    :param url: 书籍详情链接
    :return: 书籍详情字典，包含以下字段：
        - url: 书籍详情链接
        - title: 书籍标题
        - author: 书籍作者
        - cover: 书籍封面链接
        - intro: 书籍简介
        - chapterList: 章节列表，包含以下字段：
            - title: 章节标题
            - url: 章节链接
    """
    pass


def getChapterContent(url):
    """
    :param url: 章节链接
    :return: 章节内容字符串
    """
    pass
```