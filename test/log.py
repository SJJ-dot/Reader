# from http.client import HTTPConnection
# HTTPConnection.debuglevel=1
from requests import PreparedRequest, Response


def log(arg):
    if isinstance(arg, PreparedRequest):
        print("url:", arg.url)
        for key, value in arg.headers.items():
            print(f"{key}: {value}")
        print("")
        print(arg.body)
        print("")
    elif isinstance(arg, Response):
        # 打印响应头
        print("status_code:", arg.status_code)
        for key, value in arg.headers.items():
            print(f"{key}: {value}")
        print("")
        print(arg.text)
        print("")
    else:
        print(arg)
