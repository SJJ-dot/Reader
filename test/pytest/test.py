from log import log
from source import search, getDetails, getChapterContent

if __name__ == '__main__':
    result = search("我的")
    log(result)
    result = getDetails(result[0]["bookUrl"])
    log(result)
    result = getChapterContent(result["chapterList"][0]["url"])
    log(result)
