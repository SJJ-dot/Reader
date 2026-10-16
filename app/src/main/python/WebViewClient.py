from java.util import HashMap
from com.sjianjun.reader.http import WebViewClient

from exec_script import ScriptAborted, check_aborted, is_cancelled_exception


def _call(fn, *args):
    """调用前拦截 + 调用中取消。

    - 调用前：任务已取消则直接抛 ScriptAborted，不再新建 WebView；
    - 调用中：Java 侧取消（ScriptTaskCancelledException）统一转成 ScriptAborted，
      继承 BaseException，脚本的 except Exception 抓不到。
    """
    check_aborted()
    try:
        return fn(*args)
    except ScriptAborted:
        raise
    except BaseException as e:
        if is_cancelled_exception(e):
            raise ScriptAborted("脚本任务已取消")
        raise


def web_get(url, headers=None, javaScript="document.documentElement.outerHTML", timeout=20000):
    if headers is None:
        headers = {}
    if javaScript is None:
        javaScript = "document.documentElement.outerHTML"
    # 将 Python 的 dict 转换为 Java 的 HashMap
    java_headers = HashMap()
    for key, value in headers.items():
        java_headers.put(key, value)
    return _call(WebViewClient.get, url, java_headers, javaScript, timeout)


def web_post(url, postData=None, javaScript="document.documentElement.outerHTML", timeout=20000, encoding="UTF-8"):
    if postData is None:
        postData = ""
    if javaScript is None:
        javaScript = "document.documentElement.outerHTML"
    if encoding is None:
        encoding = "UTF-8"
    return _call(WebViewClient.post, url, postData, javaScript, timeout, encoding)
