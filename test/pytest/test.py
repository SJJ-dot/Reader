from log import log
from source import search, getDetails, getChapterContent

if __name__ == '__main__':
    # result = search("我的")
    # log(result)
    # result = getDetails("https://cn.ttkan.co/novel/chapters/wodedaxiaolaopo-juebandewo")
    # log(result)
    result = getChapterContent("https://cn.ttkan.co/novel/pagea/wodedaxiaolaopo-juebandewo_1.html")
    log(result)
