from java.util import HashMap
from com.sjianjun.reader.module.verification import WebViewVerificationActivity


def verification_activity_get(url, headers=None, html=None, encoding="utf-8", verificationKey=None):
    # html、encoding 参数保留以向后兼容，重构后不再使用
    if headers is None:
        headers = {}
    if verificationKey is None:
        verificationKey = ""
    # 将 Python 的 dict 转换为 Java 的 HashMap
    java_headers = HashMap()
    for key, value in headers.items():
        java_headers.put(key, value)
    return WebViewVerificationActivity.startAndWaitResult(url, java_headers, verificationKey)


def verification_activity_post(url, postData=None, verificationKey=None):
    if postData is None:
        postData = ""
    if verificationKey is None:
        verificationKey = ""
    return WebViewVerificationActivity.postAndWaitResult(url, postData, verificationKey)
