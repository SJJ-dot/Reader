import json

import requests

from MdnsHelper import discover_services
from log import log

_server_ip = None
_server_port = None


def init():
    global _server_ip, _server_port
    _server_ip, _server_port = discover_services()
    log(f"Initialized with server IP: {_server_ip}, Port: {_server_port}")


def web_get(url, headers=None, javaScript="document.documentElement.outerHTML", timeout=20000):
    init()
    server_url = f'http://{_server_ip}:{_server_port}/web_get'
    # POST请求的数据
    data = {'url': url, 'headers': headers, 'javaScript': javaScript, 'timeout': timeout}
    # 发送POST请求
    response = requests.post(server_url, json=data)
    response.encoding = 'utf-8'
    return response.text


if __name__ == '__main__':
    # Example usage
    url = 'https://www.69shuba.com/book/48273/'
    result = web_get(url)
    if not result:
        log("No result returned from web_get")
    else:
        result = json.loads(result)
        log("url:" + result.get("url"))
        log("html:" + result.get("html"))
