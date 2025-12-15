from java.util import HashMap
from com.sjianjun.reader.http import CookieMgr
from com.sjianjun.reader.module.verification import WebViewVerificationActivity

def get_cookie(url):
    return CookieMgr.getCookie(url)

def set_cookie(url, cookie):
    CookieMgr.setCookie(url, cookie)

def verification_activity_get(url, headers=None, html=None, encoding="utf-8", verificationKey=None):
    if headers is None:
            headers = {}
    if html is None:
        html = ""
    if verificationKey is None:
        verificationKey = ""
    # 将 Python 的 dict 转换为 Java 的 HashMap
    java_headers = HashMap()
    for key, value in headers.items():
        java_headers.put(key, value)
    return WebViewVerificationActivity.startAndWaitResult(url, java_headers, html, encoding, verificationKey)

def start_verification_activity(url,headers=None, html=None, encoding="utf-8", verificationKey=None):
    verification_activity_get(url, headers, html, encoding, verificationKey)
    return get_cookie(url)

