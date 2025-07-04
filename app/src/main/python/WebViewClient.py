from java.util import HashMap
from com.sjianjun.reader.http import CookieMgr
from com.sjianjun.reader.http import WebViewClient

def web_get(url,headers=None, javaScript="document.documentElement.outerHTML", timeout=20000):
    if headers is None:
        headers = {}
    if javaScript is None:
        javaScript = "document.documentElement.outerHTML"
    # 将 Python 的 dict 转换为 Java 的 HashMap
    java_headers = HashMap()
    for key, value in headers.items():
        java_headers.put(key, value)
    return WebViewClient.get(url, java_headers, javaScript,timeout)
