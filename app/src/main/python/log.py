from com.sjianjun.reader.utils import PyLog


from http.client import HTTPConnection
HTTPConnection.debuglevel=1


def log(msg):
    PyLog.i(msg)
