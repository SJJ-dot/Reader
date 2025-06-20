import time

from zeroconf import Zeroconf, ServiceBrowser, ServiceListener

from log import log


class MyListener(ServiceListener):
    def __init__(self):
        self.port = None
        self.ip = None

    def add_service(self, zeroconf, service_type, name):
        if "ReaderService._http._tcp.local." == name:
            info = zeroconf.get_service_info(service_type, name)
            if info:
                self.ip = info.parsed_addresses()[0]
                self.port = info.port
                log(f"服务:{name},地址:{self.ip}:{self.port}")

    def remove_service(self, zeroconf, service_type, name):
        log(f"服务移除: {name}")

    def update_service(self, zeroconf, service_type, name):
        log(f"服务更新: {name}")


def discover_services():
    zeroconf = Zeroconf()
    listener = MyListener()
    browser = ServiceBrowser(zeroconf, "_http._tcp.local.", listener)  # 替换为需要发现的服务类型
    try:
        while True:
            if listener.ip is not None and listener.port is not None:
                return listener.ip, listener.port
            time.sleep(1)
    finally:
        zeroconf.close()


if __name__ == "__main__":
    discover_services()
