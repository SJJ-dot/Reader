from com.sjianjun.reader.utils import PyLog


from http.client import HTTPConnection
HTTPConnection.debuglevel=1
from requests import PreparedRequest, Response

def log(arg):
    if isinstance(arg, PreparedRequest):
        PyLog.i(f"url:{arg.url}")
        for key, value in arg.headers.items():
            PyLog.i(f"{key}: {value}")
        PyLog.i("")
        PyLog.i(f"{arg.body}")
        PyLog.i("")
    elif isinstance(arg, Response):
        # 打印响应头
        for key, value in arg.headers.items():
            print(f"{key}: {value}")
        print("")
        print(f"{arg.text}")
        print("")
    else:
        PyLog.i(f"{arg}")