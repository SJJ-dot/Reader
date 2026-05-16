package sjj.legado.engine

/**
 * 阅读书源规则示例
 *"""
 * {
 *     "bookSourceComment": "备用地址：https://sma.yueyouxs.com",
 *     "bookSourceName": "阅友小说",
 *     "bookSourceType": 0,
 *     "bookSourceUrl": "http://m.suixkan.com",
 *     "customOrder": 24,
 *     "enabled": true,
 *     "enabledCookieJar": true,
 *     "enabledExplore": true,
 *     "exploreUrl": "[{\"title\":\"推荐\",\"url\":\"\",\"style\":{\"layout_flexBasisPercent\":1,\"layout_flexGrow\":1}},\n{\"title\":\"重磅推荐\",\"url\":\"/l/s/28/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.4,\"layout_flexGrow\":1}},\n{\"title\":\"男生必读\",\"url\":\"/l/s/29/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.4,\"layout_flexGrow\":1}},\n{\"title\":\"女生爱看\",\"url\":\"/l/s/30/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.4,\"layout_flexGrow\":1}},\n{\"title\":\"小编推荐\",\"url\":\"/l/s/31/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.4,\"layout_flexGrow\":1}},\n{\"title\":\"男频\",\"url\":\"\",\"style\":{\"layout_flexBasisPercent\":1,\"layout_flexGrow\":1}},\n{\"title\":\"都市人生\",\"url\":\"/l/f/1100/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"玄幻奇幻\",\"url\":\"/l/f/1101/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"武侠仙侠\",\"url\":\"/l/f/1102/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"军事历史\",\"url\":\"/l/f/1103/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"科幻末世\",\"url\":\"/l/f/1104/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"游戏体育\",\"url\":\"/l/f/1105/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"热血青春\",\"url\":\"/l/f/1106/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"悬疑灵异\",\"url\":\"/l/f/1107/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"脑洞大开\",\"url\":\"/l/f/1108/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"女频\",\"url\":\"\",\"style\":{\"layout_flexBasisPercent\":1,\"layout_flexGrow\":1}},\n{\"title\":\"现代言情\",\"url\":\"/l/f/2100/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"古代言情\",\"url\":\"/l/f/2101/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"幻想言情\",\"url\":\"/l/f/2102/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"\",\"url\":\"/l/f/2103/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"穿越时空\",\"url\":\"/l/f/2104/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"宫闱争斗\",\"url\":\"/l/f/2105/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"豪门总裁\",\"url\":\"/l/f/2106/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"婚恋爱情\",\"url\":\"/l/f/2107/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"经商种田\",\"url\":\"/l/f/2108/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"图书\",\"url\":\"\",\"style\":{\"layout_flexBasisPercent\":1,\"layout_flexGrow\":1}},\n{\"title\":\"出版读物\",\"url\":\"/l/f/3101/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"文学小说\",\"url\":\"/l/f/3102/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}},\n{\"title\":\"古代典籍\",\"url\":\"/l/f/3103/{{page}}.html\",\"style\":{\"layout_flexBasisPercent\":0.25,\"layout_flexGrow\":1}}]",
 *     "lastUpdateTime": 1680526062517,
 *     "respondTime": 1238,
 *     "ruleBookInfo": {
 *         "author": ".face-info span.0@text##.*：",
 *         "coverUrl": ".face-cover img@src",
 *         "intro": "#intro@html",
 *         "kind": ".face-info span.1:3@text&&#idNewIds@#chapter-ps-id@text##.*：",
 *         "lastChapter": "#idNewIds@.chapter-entrance@text",
 *         "name": ".face-info-title@text",
 *         "tocUrl": ".sumchapter a@href",
 *         "wordCount": ".face-info span.2@text##.*："
 *     },
 *     "ruleContent": {
 *         "content": ".con@html",
 *         "replaceRegex": "##[\\(（]本章未完.*[）\\)]|[\\(（]本章完[）\\)]"
 *     },
 *     "ruleExplore": {
 *         "author": ".v-author@text##\\s",
 *         "bookList": ".v-list-item",
 *         "bookUrl": "@onclick@js:result.match(/\\('(.*?)'\\)/)[1]",
 *         "coverUrl": "img@src",
 *         "intro": ".v-intro@text",
 *         "name": ".v-title@text",
 *         "wordCount": ".v-words@text"
 *     },
 *     "ruleSearch": {
 *         "author": ".v-author@text##\\s",
 *         "bookList": ".v-list-item",
 *         "bookUrl": "@onclick@js:result.match(/\\('(.*?)', '', ''\\)/)[1]",
 *         "coverUrl": "img@src",
 *         "intro": ".v-intro@text",
 *         "kind": ".base-label@text",
 *         "name": ".v-title@text",
 *         "wordCount": ".v-words@text"
 *     },
 *     "ruleToc": {
 *         "chapterList": ".catalog_ls li a",
 *         "chapterName": "text",
 *         "chapterUrl": "href"
 *     },
 *     "searchUrl": "/s/1.html?keyword={{key}}&page={{page}}",
 *     "weight": 0
 * }
 * 内部函数尽可能不要try catch，抛出异常由外部捕获并处理。
 */
class YueduEngine(val rule: String) {
    /**
     * 校验书源是否有效。0、1、2 分别代表：验证通过，验证失败，不支持验证
     * 如果书源不支持搜索则返回2
     */
    suspend fun verify(): Int {
        return 2
    }

    /**
     * 获取书源网站地址或者主域名地址。会添加到书城列表。也用来判断书源是否支持某个链接。返回null代表不支持获取网站地址
     */
    suspend fun getSiteUrl(): String? {
//        返回规则的 bookSourceUrl
        return null
    }

    /**
    :param query: 搜索关键词
    :return: 书籍列表，包含以下字段：
    - bookTitle: 书籍标题
    - bookUrl: 书籍详情链接
    - bookAuthor: 书籍作者(可选)
    - bookCover: 书籍封面链接(可选)
     */
    suspend fun search(query: String): String? {
        return null
    }

    /**
     *     :return: json书籍详情字典，包含以下字段：
     *         - url: 书籍详情链接
     *         - title: 书籍标题
     *         - author: 书籍作者
     *         - cover: 书籍封面链接
     *         - intro: 书籍简介
     *         - chapterList: 章节列表，包含以下字段：
     *             - title: 章节标题
     *             - url: 章节链接
     */
    suspend fun getDetails(bookUrl: String): String? {
        return null
    }

    /**
     * :param chapterUrl: 章节链接
     * :return: 章节内容字符串
     */
    suspend fun getChapterContent(chapterUrl: String): String? {
        return null
    }
}