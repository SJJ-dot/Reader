import json

import requests

from log import log

"""
{
  "bookSourceComment": "",
  "bookSourceGroup": "API",
  "bookSourceName": "文趣阁",
  "bookSourceType": 0,
  "bookSourceUrl": "http://m.nshkedu.com/",
  "bookUrlPattern": "",
  "customOrder": 8,
  "enabled": true,
  "enabledCookieJar": false,
  "enabledExplore": false,
  "exploreUrl": "",
  "header": "{\"Version-Code\":\"10000\",\"Channel\":\"mz\",\"appid\":\"wengqugexs\",\"Version-Name\":\"1.0.0\"}",
  "lastUpdateTime": 1685098328694,
  "loginUrl": "",
  "respondTime": 181938,
  "ruleBookInfo": {
    "author": "author_name",
    "coverUrl": "book_cover",
    "init": "<js>\nvar javaImport = new JavaImporter();\njavaImport.importPackage(\n    Packages.java.lang,\n    Packages.javax.crypto.spec,\n    Packages.javax.crypto,\n    Packages.android.util\n);\n\nwith(javaImport){\n    function decrypt(str){\n        var key=SecretKeySpec(String(\"ZKYm5vSUhvcG9IbXNZTG1pb2\").getBytes(),\"DESede\");\n        var iv=IvParameterSpec(String(\"01234567\").getBytes());\n        var bytes=Base64.decode(String(str).getBytes(),2);\n        var chipher=Cipher.getInstance(\"DESede/CBC/PKCS5Padding\");\n        chipher.init(2,key,iv);\n        return String(chipher.doFinal(bytes));\n    }\n}\ndecrypt(JSON.parse(result).data.replace(/(\\r\\n)|(\\n)|(\\r)/g,''))\n</js>result",
    "intro": "book_brief",
    "kind": "{{String(java.timeFormat(java.getString('$.update_time')*1000))}},{{$.category_name}},{{$.book_tags}}",
    "lastChapter": "$.chapter_new_name",
    "name": "book_name",
    "tocUrl": "@js:\nlet bid=parseInt(java.getString('$.book_id'))\nlet subPath=parseInt(bid/1000)\n\"http://s.nshkedu.com/api/book/chapter/\"+subPath+\"/\"+bid+\"/list.json\"",
    "wordCount": "book_word_num"
  },
  "ruleContent": {
    "content": "<js>\nvar javaImport = new JavaImporter();\njavaImport.importPackage(\n    Packages.java.lang,\n    Packages.javax.crypto.spec,\n    Packages.javax.crypto,\n    Packages.android.util\n);\n\nwith(javaImport){\n    function decrypt(str){\n        var key=SecretKeySpec(String(\"ZKYm5vSUhvcG9IbXNZTG1pb2\").getBytes(),\"DESede\");\n        var iv=IvParameterSpec(String(\"01234567\").getBytes());\n        var bytes=Base64.decode(String(str).getBytes(),2);\n        var chipher=Cipher.getInstance(\"DESede/CBC/PKCS5Padding\");\n        chipher.init(2,key,iv);\n        return String(chipher.doFinal(bytes));\n    }\n}\ndecrypt(JSON.parse(result).data.replace(/(\\r\\n)|(\\n)|(\\r)/g,''))\n</js>content##【.*咪咪阅读.*】"
  },
  "ruleExplore": {
    "author": "",
    "bookList": "",
    "bookUrl": "",
    "coverUrl": "",
    "intro": "",
    "kind": "",
    "lastChapter": "",
    "name": "",
    "wordCount": ""
  },
  "ruleReview": {},
  "ruleSearch": {
    "author": "author_name",
    "bookList": "<js>\nvar javaImport = new JavaImporter();\njavaImport.importPackage(\n    Packages.java.lang,\n    Packages.javax.crypto,\n    Packages.javax.crypto.spec,\n    Packages.android.util\n);\n\nwith(javaImport){\n    function decrypt(str){\n        var key=SecretKeySpec(String(\"ZKYm5vSUhvcG9IbXNZTG1pb2\").getBytes(),\"DESede\");\n        var iv=IvParameterSpec(String(\"01234567\").getBytes());\n        var bytes=Base64.decode(String(str).getBytes(),2);\n        var chipher=Cipher.getInstance(\"DESede/CBC/PKCS5Padding\");\n        chipher.init(2,key,iv);\n        return String(chipher.doFinal(bytes));\n    }\n}\ndecrypt(JSON.parse(result).data.replace(/(\\r\\n)|(\\n)|(\\r)/g,''))\n</js>result",
    "bookUrl": "@js:\nlet bid=parseInt(java.getString('$.book_id'))\nlet subPath=parseInt(bid/1000)\n\"http://s.nshkedu.com/api/book/detail/\"+subPath+\"/\"+bid+\".json\"",
    "coverUrl": "book_cover",
    "intro": "book_brief",
    "kind": "{{String(java.timeFormat(java.getString('$.chapter_time')*1000))}},{{$.category_name}},{{$.book_tags}},{{$.book_level}}分",
    "lastChapter": "$.chapter_new_name",
    "name": "book_name@put:{bid:$.book_id}",
    "wordCount": "book_word_num"
  },
  "ruleToc": {
    "chapterList": "<js>\nvar javaImport = new JavaImporter();\njavaImport.importPackage(\n    Packages.java.lang,\n    Packages.javax.crypto.spec,\n    Packages.javax.crypto,\n    Packages.android.util\n);\n\nwith(javaImport){\n    function decrypt(str){\n        var key=SecretKeySpec(String(\"ZKYm5vSUhvcG9IbXNZTG1pb2\").getBytes(),\"DESede\");\n        var iv=IvParameterSpec(String(\"01234567\").getBytes());\n        var bytes=Base64.decode(String(str).getBytes(),2);\n        var chipher=Cipher.getInstance(\"DESede/CBC/PKCS5Padding\");\n        chipher.init(2,key,iv);\n        return String(chipher.doFinal(bytes));\n    }\n}\ndecrypt(JSON.parse(result).data.replace(/(\\r\\n)|(\\n)|(\\r)/g,''))\n</js>result",
    "chapterName": "chapter_name",
    "chapterUrl": "@js:baseUrl.replace('/list','/{{$._id}}')",
    "isVip": "",
    "updateTime": "{{$.words_count}} 字"
  },
  "searchUrl": "http://m.nshkedu.com/search/book/result,{\"method\":\"POST\",\"body\":\"kw={{key}}&pn={{page}}&is_author=0\"}",
  "weight": 50
}
"""

from Crypto.Cipher import DES3
from Crypto.Util.Padding import unpad
import base64
import re


def decrypt(ciphertext):
    # 3DES密钥和初始化向量
    key = b"ZKYm5vSUhvcG9IbXNZTG1pb2"
    iv = b"01234567"

    # 使用base64解码密文
    ciphertext = base64.b64decode(re.sub(r'(\r\n)|(\n)|(\r)', '', ciphertext))

    # 创建cipher对象
    cipher = DES3.new(key, DES3.MODE_CBC, iv=iv)

    # 解密
    plaintext = unpad(cipher.decrypt(ciphertext), DES3.block_size)

    return plaintext.decode('utf-8')


def search(query):
    """
    书源搜索函数
    :param query: 搜索关键词
    :return [{"bookTitle":"书名", "bookUrl": "书籍链接", "bookAuthor": "作者"}, ...] 搜索结果列表
    """
    # POST请求的URL
    url = 'http://m.nshkedu.com/search/book/result'
    # POST请求的数据
    data = {'kw': query.encode('utf-8'), "is_author": 0, "pn": 1}
    headers = {"Version-Code": "10000", "Channel": "mz", "appid": "wengqugexs", "Version-Name": "1.0.0"}
    # 发送POST请求
    response = requests.post(url, data=data, headers=headers)
    response.encoding = 'utf-8'
    log(response.text)
    data = decrypt(json.loads(response.text)["data"])
    log(data)
    # 创建BeautifulSoup对象
    # "bookUrl": "@js:\nlet bid=parseInt(java.getString('$.book_id'))\nlet subPath=parseInt(bid/1000)\n\"http://s.nshkedu.com/api/book/detail/\"+subPath+\"/\"+bid+\".json\"",
    data = json.loads(data)["result"]
    books = []
    for bookInfo in data:
        bid = int(bookInfo["book_id"])
        books.append({
            "bookTitle": bookInfo["book_name"],
            "bookUrl": f"http://s.nshkedu.com/api/book/detail/" + str(int(bid / 1000)) + "/" + str(bid) + ".json",
            "bookAuthor": bookInfo["author_name"],
        })

    return books


def getDetails(book_url):
    """
    获取章节列表
    :param book_url: 书籍链接
    :return
    {"url": "书籍链接", "title": "书名", "author": "作者", "intro": "简介", "cover": "封面链接",
     "chapterList": [{"title": "章节名", "URL": "章节链接"}, ...]
     }
    """

    # url book_url
    # 发送get请求
    response = requests.get(book_url)
    response.encoding = "utf-8"
    info = {}
    # 创建BeautifulSoup对象
    soup = json.loads(decrypt(json.loads(response.text)["data"]))["result"]
    log(soup)
    info["url"] = book_url
    # 书名
    info["title"] = soup["book_name"]
    # 作者 作    者：黄金国巫妖
    info["author"] = soup["author_name"]
    # 简介
    info["intro"] = soup["book_brief"]
    # 封面
    info["cover"] = soup["book_cover"]
    # 章节列表
    info["chapterList"] = []
    chapter_url = f"http://s.nshkedu.com/api/book/chapter/" + str(int(soup["book_id"] / 1000)) + "/" + str(
        soup["book_id"]) + "/list.json"
    response = requests.get(chapter_url)
    response.encoding = "utf-8"
    data = json.loads(decrypt(json.loads(response.text)["data"]))["result"]
    log(data)
    for chapter in data:
        info["chapterList"].append({
            "title": chapter["chapter_name"],
            "url": f"http://s.nshkedu.com/api/book/chapter/" + str(int(soup["book_id"] / 1000)) + "/" + str(
                soup["book_id"]) + "/" + str(chapter["_id"]) + ".json"
        })

    return info


def getChapterContent(chapter_url):
    """
    获取章节内容
    :param chapter_url: 章节链接
    :return: 章节内容 html格式
    """
    # 发送get请求
    response = requests.get(chapter_url)
    response.encoding = "utf-8"
    # 创建BeautifulSoup对象
    soup = json.loads(decrypt(json.loads(response.text)["data"]))
    log(soup)
    # 章节内容 html
    content = soup["content"]
    return content.replace("\n", "<br>")
