from java.util import HashMap
from com.sjianjun.reader.http import CookieMgr
from com.sjianjun.reader.module.verification import WebViewVerificationActivity

def get_cookie(url):
    return CookieMgr.getCookie(url)

def set_cookie(url, cookie):
    CookieMgr.setCookie(url, cookie)

def start_verification_activity(url,headers=None, html=None, encoding="utf-8"):
    if headers is None:
        headers = {}
    if html is None:
        html = ""
    # 将 Python 的 dict 转换为 Java 的 HashMap
    java_headers = HashMap()
    for key, value in headers.items():
        java_headers.put(key, value)
    WebViewVerificationActivity.startAndWaitResult(url, java_headers, html,encoding)
    return get_cookie(url)
