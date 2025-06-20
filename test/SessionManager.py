import requests

from MdnsHelper import discover_services
from log import log

_server_ip = None
_server_port = None
if _server_ip is None or _server_port is None:
    _server_ip, _server_port = discover_services()


def get_cookie(url):
    server_url = f'http://{_server_ip}:{_server_port}/getCookie'
    # POST请求的数据
    data = {'url': url}
    # 发送POST请求
    response = requests.post(server_url, json=data)
    response.encoding = 'utf-8'
    log(f'Get cookies for {url}: {response.text}')
    return response.text


def set_cookie(url, cookies):
    server_url = f'http://{_server_ip}:{_server_port}/setCookie'
    # POST请求的数据
    data = {'url': url, 'cookies': cookies}
    # 发送POST请求
    response = requests.post(server_url, json=data)
    response.encoding = 'utf-8'
    log(f'Set cookies for {url}: {response.text} (cookies: {cookies})')


def start_verification_activity(url, headers=None, html=None):
    server_url = f'http://{_server_ip}:{_server_port}/start_verification_activity'
    # POST请求的数据
    data = {'url': url, 'headers': headers, 'html': html}
    # 发送POST请求
    response = requests.post(server_url, json=data)
    response.encoding = 'utf-8'
    log(f'Start verification activity finish for {url}: {response.text}')
    return response.text


if __name__ == '__main__':
    # Example usage
    url = 'https://www.69shuba.com/modules/article/search.php'
    cookies = get_cookie(url)
    set_cookie(url, cookies)
    start_verification_activity(url)