## 书源制作教程
### 1、开发环境
- 语言：`Python3.12`
- 推荐IDE：`PyCharm`
- 除python基础库外的内置库：`requests`、`beautifulsoup4`、`pycryptodome`
### 2、书源开发步骤
1. 将存储库下载到本地，并切换到`BookSource`分支
2. 使用`PyCharm`打开目录：`Reader\test`
3. 在`Reader/test/py`目录下创建对应书源的`py`脚本：`xx小说.py`
4. 复制模板到`xx小说.py`中：
   - 有人机验证，选择[顶点小说](https://github.com/SJJ-dot/Reader/blob/BookSource/test/py/%E9%A1%B6%E7%82%B9%E5%B0%8F%E8%AF%B4.py)并打开APP设置页的测试接口，测试接口默认关闭，开启时可在局域网内调用APP内的人机验证接口
   - 无人机验证，选择[小原文学网](https://github.com/SJJ-dot/Reader/blob/BookSource/test/py/%E5%B0%8F%E5%8E%9F%E6%96%87%E5%AD%A6%E7%BD%91.py)”。
   - 如果时其他书源转修仙书源，参考：[yuedu.py](yuedu.py)，阅读书源原生支持，可直接导入不需要转换
5. 将新书源域名告诉`AI`，让`AI`修改直到脚本测试通过，输出章节内容即可
6. 更新书源：运行脚本`Reader/test/update_source.py`；脚本会将`Reader/test/py`目录下的脚本打包更新到`Reader/test/BookSource`目录下：[default.json](BookSource/default.json) 和 [default.json.gzip](BookSource/default.json.gzip)
7. 如果有特别的说明可以手动编写 [default.json](BookSource/default.json) 找到对应书源，书源字典可以备注，例如：`comment:"需要梯子"`
8. 在`APP`书源管理页面填写`BookSource`目录下的文件链接，推荐`gzip`文件优先


### 书源函数接口（具体编写参考已有的脚本：Reader/test/py）
```python
def verify():
    """
    可选，部分书籍不支持搜索功能时提供，避免验证书源失败
    验证书源是否可用，返回0、1、2 分别代表：验证通过，验证失败，不支持验证
    """
    pass

def getSiteUrl():
    """
    可选，如果不包含该函数，书源无法使用链接直接导入，也无法在书城进行导入
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


def getDetails(url, lastChapterUrl=None):
    """
    :param url: 书籍详情链接
    :param lastChapterUrl: 本地已缓存的最新章节链接（可选）。传入时做增量更新检查，
        避免每次全量拉取目录，返回给本地用于拼接的新增章节：
        - 不传：首次加载，返回完整目录
        - 传入且无更新：chapterList 返回空列表
        - 传入且有更新：返回包含本地最新章节在内的一段尾部章节（旧->新排序），
          由本地按章节链接去重拼接
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

