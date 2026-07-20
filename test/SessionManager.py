import requests

from MdnsHelper import discover_services
from log import log

_server_ip = None
_server_port = None


def init():
    global _server_ip, _server_port
    _server_ip, _server_port = discover_services()
    log(f"Initialized with server IP: {_server_ip}, Port: {_server_port}")


def verification_activity_get(url, headers=None, verificationKey=None):
    init()
    server_url = f'http://{_server_ip}:{_server_port}/verification_activity_get'
    # POST请求的数据
    data = {'url': url, 'headers': headers, 'verificationKey': verificationKey}
    # 发送POST请求
    response = requests.post(server_url, json=data)
    response.encoding = 'utf-8'
    return response.text


def verification_activity_post(url, postData=None, verificationKey=None):
    init()
    server_url = f'http://{_server_ip}:{_server_port}/verification_activity_post'
    # POST请求的数据
    data = {'url': url, 'postData': postData, 'verificationKey': verificationKey}
    # 发送POST请求
    response = requests.post(server_url, json=data)
    response.encoding = 'utf-8'
    log(f'Verification activity post finish for {url}: {response.text}')
    return response.text


if __name__ == '__main__':
    # Example usage
    url = 'https://www.69shuba.com/modules/article/search.php'
