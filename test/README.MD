## 书源制作教程
- 如果不会编程也不会使用AI辅助编程，那我建议你使用「开源阅读」的书源。
### 1、开发环境
- 语言：Python3.12 
- 推荐ide：PyCharm
- 除python基础库外的内置库：requests、beautifulsoup4、pycryptodome
### 2、书源开发步骤
1. 将存储库下载到本地，并切换到BookSource分支
2. 使用PyCharm打开存储库目录：Reader\test
3. 模版选择：网站有人机验证选择“顶点小说.py”、不需要人机验证选择“小原文学网.py”。
4. 按下方的书源函数接口要求编辑临时书源测试文件Reader/test/source.py（也可以直接在Reader/test/py目录下创建对应书源的py脚本）。Reader/test/source.py测试通过后，在Reader/test/py目录下创建对应的书源文件或者是覆盖已有的脚本（如果是更新或者修复BUG）
5. Reader/test/py目录下文件更新后，运行脚本Reader/test/update_source.py；脚本会将Reader/test/py目录下的脚本打包更新到Reader/test/BookSource目录下。到这里脚本就制作完成了。
6. 在APP书源管理页面填写BookSource目录下的文件链接，推荐gzip文件优先


### 书源函数接口（具体编写参考已有的脚本：Reader/test/py）
```python
def verify():
    """
    可选，部分书籍不支持搜索功能时提供，避免验证书源失败
    验证书源是否可用，返回数字或字符串：0、1、2 分别代表：验证通过，验证失败，不支持验证
    """
    pass

def getSiteUrl():
    """
    可选，如果不包含该函数，书源无法使用链接直接导入，也无法在书城进行导入
    :return: 字符串，书源网址，例如：https://www.69shuba.com，用于直接导入书籍详情页链接
    """
    pass

def search(query):
    """
    :param query: 搜索关键词
    :return: json书籍列表，包含以下字段：
        - bookTitle: 书籍标题
        - bookUrl: 书籍详情链接
        - bookAuthor: 书籍作者(可选)
        - bookCover: 书籍封面链接(可选)
        
    """
    pass


def getDetails(url):
    """
    :param url: 书籍详情链接
    :return: json书籍详情字典，包含以下字段：
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
    :return: 字符串，章节内容
    """
    pass
```
### 机器人验证接口
对于有机器人验证的网站使用下面的接口：
```python

def web_get(url):
    """
    使用后台webview执行get请求
    :param url
    :return: 返回json:{url:"",html:""}
    """

def web_post(url,postData,encoding):
    """
    使用后台webview执行post请求
    :param url
    :param postData body
    :param encoding 传输时body编码默认utf-8
    :return: 返回json:{url:"",html:""}
    """

def verification_activity_get(url):
    """
    使用前台带界面的webview执行get请求，验证通过后需要人工返回
    :param url
    :return: 返回json:{url:"",html:""}
    """
def verification_activity_post(url, postData=None):
    """
    使用前台带界面的webview执行post请求，验证通过后需要人工返回
    :param url
    :param postData body字符串格式
    :return: 返回json:{url:"",html:""}
    """
```
一般流程：优先使用web_get请求失败或者响应数据获取失败后再使用verification_activity_get或者verification_activity_post

