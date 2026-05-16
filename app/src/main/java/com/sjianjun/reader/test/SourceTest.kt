package com.sjianjun.reader.test

import com.sjianjun.reader.bean.BookSource
import kotlinx.coroutines.delay
import sjj.alog.Log

object SourceTest {
    suspend fun test() {
        val source = BookSource()
        source.lauanage = BookSource.Language.yuedu
        source.js = """
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
        """.trimIndent()
        delay(5000)
        Log.i("=================")
        val results = source.search("金刚")
        Log.i("搜索结果：$results")

    }
}