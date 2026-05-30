import json
import re
from urllib.parse import quote, urljoin

import requests
from bs4 import BeautifulSoup

#
# def select_source():
#     source_url = "https://legado.aoaostar.com/sources/71e56d4f.json"
#     response = json.loads(requests.get(source_url).text)
#     for item in response:
#         bookSourceName = item['bookSourceName']
#         if bookSourceName != "阅友小说":
#             continue
#         book_source = json.dumps(item, indent=4, ensure_ascii=False)
#         print(">>>>>>>>================================")
#         print(book_source)
#         print("<<<<<<<<================================")


# 阅读3.0书源兼容脚本。
# 阅读3.0书源规则：阅读3.0书源规则.pdf
# 阅读书源json字符串。APP拼接阅读规则和Python模板
yuedu_source = r"""
{
    "bookSourceComment": "备用地址：https://sma.yueyouxs.com",
    "bookSourceName": "阅友小说",
    "bookSourceType": 0,
    "bookSourceUrl": "http://m.suixkan.com",
    "customOrder": 24,
    "enabled": true,
    "enabledCookieJar": true,
    "enabledExplore": true,
    "exploreUrl": "[{\"title\":\"推荐\",\"url\":\"\",\"style\":{\"layout_flexBasisPercent\":1,\"layout_flexGrow\":1}},\n{\"title\":\"重磅推荐\",\"url\":\"/l/s/28/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.4,\"layout_flexGrow\":1}},\n{\"title\":\"男生必读\",\"url\":\"/l/s/29/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.4,\"layout_flexGrow\":1}},\n{\"title\":\"女生爱看\",\"url\":\"/l/s/30/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.4,\"layout_flexGrow\":1}},\n{\"title\":\"小编推荐\",\"url\":\"/l/s/31/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.4,\"layout_flexGrow\":1}},\n{\"title\":\"男频\",\"url\":\"\",\"style\":{\"layout_flexBasisPercent\":1,\"layout_flexGrow\":1}},\n{\"title\":\"都市人生\",\"url\":\"/l/f/1100/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"玄幻奇幻\",\"url\":\"/l/f/1101/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"武侠仙侠\",\"url\":\"/l/f/1102/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"军事历史\",\"url\":\"/l/f/1103/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"科幻末世\",\"url\":\"/l/f/1104/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"游戏体育\",\"url\":\"/l/f/1105/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"热血青春\",\"url\":\"/l/f/1106/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"悬疑灵异\",\"url\":\"/l/f/1107/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"脑洞大开\",\"url\":\"/l/f/1108/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"女频\",\"url\":\"\",\"style\":{\"layout_flexBasisPercent\":1,\"layout_flexGrow\":1}},\n{\"title\":\"现代言情\",\"url\":\"/l/f/2100/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"古代言情\",\"url\":\"/l/f/2101/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"幻想言情\",\"url\":\"/l/f/2102/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"\",\"url\":\"/l/f/2103/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"穿越时空\",\"url\":\"/l/f/2104/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"宫闱争斗\",\"url\":\"/l/f/2105/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"豪门总裁\",\"url\":\"/l/f/2106/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"婚恋爱情\",\"url\":\"/l/f/2107/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"经商种田\",\"url\":\"/l/f/2108/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"图书\",\"url\":\"\",\"style\":{\"layout_flexBasisPercent\":1,\"layout_flexGrow\":1}},\n{\"title\":\"出版读物\",\"url\":\"/l/f/3101/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"文学小说\",\"url\":\"/l/f/3102/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"古代典籍\",\"url\":\"/l/f/3103/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}}]",
    "lastUpdateTime": 1680526062517,
    "respondTime": 1238,
    "ruleBookInfo": {
        "author": ".face-info span.0@text##.*：",
        "coverUrl": ".face-cover img@src",
        "intro": "#intro@html",
        "kind": ".face-info span.1:3@text&&#idNewIds@#chapter-ps-id@text##.*：",
        "lastChapter": "#idNewIds@.chapter-entrance@text",
        "name": ".face-info-title@text",
        "tocUrl": ".sumchapter a@href",
        "wordCount": ".face-info span.2@text##.*："
    },
    "ruleContent": {
        "content": ".con@html",
        "replaceRegex": "##[\\(（]本章未完.*[）\\)]|[\\(（]本章完[）\\)]"
    },
    "ruleExplore": {
        "author": ".v-author@text##\\s",
        "bookList": ".v-list-item",
        "bookUrl": "@onclick@js:result.match(/\\('(.*?)'\\)/)[1]",
        "coverUrl": "img@src",
        "intro": ".v-intro@text",
        "name": ".v-title@text",
        "wordCount": ".v-words@text"
    },
    "ruleSearch": {
        "author": ".v-author@text##\\s",
        "bookList": ".v-list-item",
        "bookUrl": "@onclick@js:result.match(/\\('(.*?)', '', ''\\)/)[1]",
        "coverUrl": "img@src",
        "intro": ".v-intro@text",
        "kind": ".base-label@text",
        "name": ".v-title@text",
        "wordCount": ".v-words@text"
    },
    "ruleToc": {
        "chapterList": ".catalog_ls li a",
        "chapterName": "text",
        "chapterUrl": "href"
    },
    "searchUrl": "/s/1.html?keyword={{key}}&page={{page}}",
    "weight": 0
}
"""
yuedu_source = json.loads(yuedu_source.strip())


def _split_replace_rule(rule_text):
    parts = rule_text.split("##")
    base_rule = parts[0]
    replace_regex = parts[1] if len(parts) > 1 else ""
    replacement = parts[2] if len(parts) > 2 else ""
    replace_first = len(parts) > 3
    return base_rule, replace_regex, replacement, replace_first


def _apply_replace(value, replace_regex, replacement="", replace_first=False):
    if not replace_regex:
        return value
    if replace_first:
        match = re.search(replace_regex, value)
        if not match:
            return ""
        return re.sub(replace_regex, replacement, match.group(0), count=1)
    return re.sub(replace_regex, replacement, value)


def _split_rule_logic(rule_text):
    for operator in ("||", "&&", "%%"):
        if operator in rule_text:
            parts = [part.strip() for part in rule_text.split(operator) if part.strip()]
            return operator, parts
    return "", [rule_text]


def _interleave_lists(lists):
    merged = []
    max_len = max((len(lst) for lst in lists), default=0)
    for i in range(max_len):
        for lst in lists:
            if i < len(lst):
                merged.append(lst[i])
    return merged


def _extract_js_match(rule, element):
    # 支持: @onclick@js:result.match(/.../)[1]
    js_prefix = "@js:"
    attr_name, js_expr = rule.split(js_prefix, 1)
    attr_name = attr_name.strip("@")
    raw_value = element.get(attr_name, "") if hasattr(element, "get") else ""

    match = re.search(r"result\.match\(/(.*?)/\)\[(\d+)\]", js_expr)
    if not match:
        return ""

    pattern = match.group(1)
    group_index = int(match.group(2))
    result = re.search(pattern, raw_value)
    if not result:
        return ""

    try:
        return result.group(group_index)
    except IndexError:
        return ""


def _parse_selector_target(base_rule):
    if "@" not in base_rule:
        if base_rule in {"text", "html", "href", "src", "textNodes", "ownText", "all"}:
            return "", base_rule
        return base_rule, "text"

    if base_rule.startswith("@"):
        selector, target = "", base_rule.strip("@")
    else:
        selector, target = base_rule.rsplit("@", 1)
    return selector, target


def _select_nodes(element, selector, multiple=True):
    if not selector:
        return [element]

    selector_match = re.match(r"^(.*)\.(\d+)(?::(\d+))?$", selector)
    if selector_match:
        base_selector = selector_match.group(1).strip()
        start_index = int(selector_match.group(2))
        end_index = selector_match.group(3)
        all_nodes = element.select(base_selector)
        if end_index is None:
            return all_nodes[start_index:start_index + 1]
        return all_nodes[start_index:int(end_index)]

    if "@" in selector:
        # 兼容 #id@.class@a 这类链式规则
        node = element
        for step in [part.strip() for part in selector.split("@") if part.strip()]:
            node = node.select_one(step)
            if node is None:
                return []
        return [node]

    return element.select(selector) if multiple else [element.select_one(selector)]


def _extract_list_single_rule(rule, element):
    base_rule, replace_regex, replacement, replace_first = _split_replace_rule(rule)

    if "@js:" in base_rule:
        value = _extract_js_match(base_rule, element)
        value = _apply_replace(value, replace_regex, replacement, replace_first).strip()
        return [value] if value else []

    selector, target = _parse_selector_target(base_rule)
    nodes = _select_nodes(element, selector, multiple=True)
    nodes = [node for node in nodes if node is not None]
    if not nodes:
        return []

    values = []
    for node in nodes:
        if target == "text":
            values.append(node.get_text(strip=True))
        elif target == "textNodes":
            text_nodes = [text.strip() for text in node.find_all(string=True, recursive=False) if text.strip()]
            values.append("\n".join(text_nodes))
        elif target == "ownText":
            values.append("".join(node.find_all(string=True, recursive=False)).strip())
        elif target in {"html", "all"}:
            values.append(node.decode_contents() if target == "html" else str(node))
        else:
            values.append(node.get(target, ""))

    cleaned = []
    for value in values:
        value = _apply_replace(value or "", replace_regex, replacement, replace_first).strip()
        if value:
            cleaned.append(value)
    return cleaned


def _extract_list_by_rule(rule, element):
    if not rule:
        return []

    operator, parts = _split_rule_logic(rule)
    if not operator:
        return _extract_list_single_rule(rule, element)

    result_groups = [_extract_list_single_rule(part, element) for part in parts]
    if operator == "||":
        for group in result_groups:
            if group:
                return group
        return []
    if operator == "&&":
        merged = []
        for group in result_groups:
            merged.extend(group)
        return merged
    return _interleave_lists(result_groups)


def _extract_by_rule(rule, element):
    if not rule:
        return ""

    values = _extract_list_by_rule(rule, element)
    if not values:
        return ""
    return "".join(values)


def _supports_search():
    rule_search = yuedu_source.get("ruleSearch") or {}
    search_url = yuedu_source.get("searchUrl", "")
    return bool(search_url and rule_search.get("bookList"))

def verify(query):
    """
    校验书源是否可用。
    返回0、1、2 分别代表：验证通过，验证失败，不支持验证
    """
    if not _supports_search():
        return 0

    books = search(query)
    if not books or len(books) == 0:
        raise Exception("搜索失败")

    details = getDetails(books[0].get("bookUrl", ""))

    chapter_list = details.get("chapterList") or []
    if not chapter_list or len(chapter_list) == 0:
        raise Exception("获取章节列表失败")

    content = getChapterContent(chapter_list[0].get("url", ""))

    return  0 if len(content) > 10 else 1


def getSiteUrl():
    """
    可选，如果不包含该函数，书源无法使用链接直接导入，也无法在书城进行导入
    :return: 书源网址，例如：https://www.69shuba.com，用于直接导入书籍详情页链接
    """
    return str(yuedu_source["bookSourceUrl"])

def search(query):
    """
    :param query: 搜索关键词
    :return: 书籍列表，包含以下字段：
        - bookTitle: 书籍标题
        - bookUrl: 书籍详情链接
        - bookAuthor: 书籍作者(可选)
        - bookCover: 书籍封面链接(可选)

    """
    rule = yuedu_source.get("ruleSearch", {})
    base_url = getSiteUrl()

    search_url_tpl = yuedu_source.get("searchUrl", "")
    search_url = search_url_tpl.replace("{{key}}", quote(query)).replace("{{page}}", "1")
    search_url = urljoin(base_url, search_url)

    response = requests.get(search_url, timeout=15)
    response.raise_for_status()

    soup = BeautifulSoup(response.text, "html.parser")
    book_selector = rule.get("bookList", "")
    if not book_selector:
        return []

    books = []
    for item in soup.select(book_selector):
        title = _extract_by_rule(rule.get("name", ""), item)
        detail_url = _extract_by_rule(rule.get("bookUrl", ""), item)
        author = _extract_by_rule(rule.get("author", ""), item)
        cover = _extract_by_rule(rule.get("coverUrl", ""), item)

        if not title or not detail_url:
            continue

        books.append({
            "bookTitle": title,
            "bookUrl": urljoin(base_url, detail_url),
            "bookAuthor": author,
            "bookCover": urljoin(base_url, cover) if cover else "",
        })

    return books


def getDetails(url):
    """
    :param url: 书籍详情链接
    :return: 书籍详情字典，包含以下字段：
        - url: 书籍详情链接
        - title: 书籍标题
        - author: 书籍作者
        - cover: 书籍封面链接
        - intro: 书籍简介
        - chapterList: 章节列表，包含以下字段：
            - title: 章节标题
            - url: 章节链接
    """
    base_url = getSiteUrl()
    rule_book = yuedu_source.get("ruleBookInfo", {})
    rule_toc = yuedu_source.get("ruleToc", {})

    response = requests.get(url, timeout=15)
    response.raise_for_status()
    soup = BeautifulSoup(response.text, "html.parser")

    toc_url = _extract_by_rule(rule_book.get("tocUrl", ""), soup)
    toc_url = urljoin(url, toc_url) if toc_url else url

    book = {
        "url": url,
        "title": _extract_by_rule(rule_book.get("name", ""), soup),
        "author": _extract_by_rule(rule_book.get("author", ""), soup),
        "cover": urljoin(base_url, _extract_by_rule(rule_book.get("coverUrl", ""), soup)),
        "intro": _extract_by_rule(rule_book.get("intro", ""), soup),
        "chapterList": [],
    }

    chapter_selector = rule_toc.get("chapterList", "")
    reverse_result = False
    if chapter_selector.startswith("-"):
        reverse_result = True
        chapter_selector = chapter_selector[1:]
    elif chapter_selector.startswith("+"):
        chapter_selector = chapter_selector[1:]

    chapter_name_rule = rule_toc.get("chapterName", "text")
    chapter_url_rule = rule_toc.get("chapterUrl", "href")
    next_toc_rule = rule_toc.get("nextTocUrl", "")

    visited_pages = set()
    pending_pages = [toc_url]
    chapter_seen = set()

    while pending_pages:
        page_url = pending_pages.pop(0)
        if page_url in visited_pages:
            continue
        visited_pages.add(page_url)

        toc_response = requests.get(page_url, timeout=15)
        toc_response.raise_for_status()
        toc_soup = BeautifulSoup(toc_response.text, "html.parser")

        for chapter_el in toc_soup.select(chapter_selector):
            chapter_name = _extract_by_rule(chapter_name_rule, chapter_el)
            chapter_url = _extract_by_rule(chapter_url_rule, chapter_el)
            if not chapter_name or not chapter_url:
                continue
            chapter_full_url = urljoin(page_url, chapter_url)
            chapter_key = (chapter_name, chapter_full_url)
            if chapter_key in chapter_seen:
                continue
            chapter_seen.add(chapter_key)
            book["chapterList"].append({
                "title": chapter_name,
                "url": chapter_full_url,
            })

        if next_toc_rule:
            next_urls = _extract_list_by_rule(next_toc_rule, toc_soup)
            for next_url in next_urls:
                full_next_url = urljoin(page_url, next_url)
                if full_next_url not in visited_pages and full_next_url not in pending_pages:
                    pending_pages.append(full_next_url)

    if reverse_result:
        book["chapterList"].reverse()

    return book


def getChapterContent(url):
    """
    :param url: 章节链接
    :return: 章节内容字符串
    """
    rule_content = yuedu_source.get("ruleContent", {})
    content_rule = rule_content.get("content", "")
    replace_rule = rule_content.get("replaceRegex", "")
    next_content_rule = rule_content.get("nextContentUrl", "")

    visited = set()

    def extract_content_blocks_from_soup(content_rule_text, soup):
        # 章节正文规则通常需要提取匹配到的全部节点（例如 .con 有多段分页内容）
        base_rule = content_rule_text.split("##", 1)[0]
        if "@" in base_rule:
            selector, target = base_rule.rsplit("@", 1)
        else:
            selector, target = base_rule, "text"

        if not selector:
            return [_extract_by_rule(content_rule_text, soup)]

        selector_match = re.match(r"^(.*)\.(\d+)(?::(\d+))?$", selector)
        if selector_match:
            # 显式下标规则仍按下标语义处理
            return [_extract_by_rule(content_rule_text, soup)]

        nodes = soup.select(selector)
        if not nodes:
            return []

        return _extract_list_by_rule(content_rule_text, soup)

    def fetch_page_content(page_url):
        if page_url in visited:
            return ""
        visited.add(page_url)

        response = requests.get(page_url, timeout=15)
        response.raise_for_status()
        soup = BeautifulSoup(response.text, "html.parser")

        content_blocks = extract_content_blocks_from_soup(content_rule, soup)
        if replace_rule:
            _, replace_regex, replacement, replace_first = _split_replace_rule(replace_rule)
            if replace_regex:
                content_blocks = [
                    _apply_replace(block, replace_regex, replacement, replace_first).strip()
                    for block in content_blocks
                ]
        content_html = "".join([block for block in content_blocks if block])

        next_page_url = ""
        if next_content_rule:
            next_urls = _extract_list_by_rule(next_content_rule, soup)
            if next_urls:
                next_page_url = urljoin(page_url, next_urls[0])
        else:
            for a in soup.select("a"):
                if a.get_text(strip=True) == "下一页":
                    href = a.get("href", "")
                    if href:
                        next_page_url = urljoin(page_url, href)
                    break

        if next_page_url:
            return content_html + fetch_page_content(next_page_url)
        return content_html

    return fetch_page_content(url)


if __name__ == '__main__':
    # print(getSiteUrl())
    print(">>>>>>search")
    result = search("我的")
    print(result)
    print(">>>>>>getDetails")
    result = getDetails(result[0]["bookUrl"])
    print(result)
    print(">>>>>>getChapterContent")
    result = getChapterContent(result["chapterList"][0]["url"])
    print(result)
