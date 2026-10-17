from com.sjianjun.reader.utils import PyLog

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
            PyLog.i(f"{key}: {value}")
        PyLog.i("")
#         PyLog.i(f"{arg.text}")
#         PyLog.i("")
    else:
        PyLog.i(f"{arg}")