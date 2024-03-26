from com.sjianjun.reader.utils import PyLog


from http.client import HTTPConnection
HTTPConnection.debuglevel=1
from requests import PreparedRequest

def log(arg):
    if isinstance(arg, PreparedRequest):
        PyLog.i(f"url:{arg.url}")
        for key, value in arg.headers.items():
            PyLog.i(f"{key}: {value}")
        PyLog.i("")
        PyLog.i(arg.body)
        PyLog.i("")
    else:
        PyLog.i(arg)