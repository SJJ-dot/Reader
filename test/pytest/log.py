# from http.client import HTTPConnection
# HTTPConnection.debuglevel=1
from requests import PreparedRequest


def log(arg):
    if isinstance(arg, PreparedRequest):
        print(arg.url)
        for key, value in arg.headers.items():
            print(f"{key}: {value}")
        print("")
        print(arg.body)
        print("")
    else:
        print(arg)
