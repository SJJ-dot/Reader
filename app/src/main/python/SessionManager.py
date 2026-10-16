from java.util import HashMap
from com.sjianjun.reader.module.verification import WebViewVerificationActivity

from exec_script import ScriptAborted, check_aborted, is_cancelled_exception


def _call(fn, *args):
    """调用前拦截 + 调用中取消。

    - 调用前：任务已取消则直接抛 ScriptAborted，不再弹出验证页；
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
    return _call(WebViewVerificationActivity.startAndWaitResult, url, java_headers, verificationKey)


def verification_activity_post(url, postData=None, verificationKey=None):
    if postData is None:
        postData = ""
    if verificationKey is None:
        verificationKey = ""
    return _call(WebViewVerificationActivity.postAndWaitResult, url, postData, verificationKey)
