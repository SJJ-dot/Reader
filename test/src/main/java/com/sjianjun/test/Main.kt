package com.sjianjun.test

import com.sjianjun.test.bean.BookSource
import java.io.File

val test = File("./test/src/main/java/com/sjianjun/test/test.js").readText()


suspend fun main(args: Array<String>) {
    val javaScript by lazy {
        BookSource().apply {
            name = "测试脚本"
            js = test
        }
    }
    val resultList = javaScript.search("我的")
    println("搜索到结果数量：${resultList?.size}")
    if (resultList.isNullOrEmpty()) {
        return
    }
    var details = javaScript.getDetails(resultList[0].bookUrl)
    println("搜索到章节数量：${details?.chapterList?.size}")
    if (details?.chapterList.isNullOrEmpty()) {
        return
    }

    details = javaScript.getDetails(details!!.url)
    println("详情页再次加载搜索到章节数量：${details?.chapterList?.size}")
    if (details?.chapterList.isNullOrEmpty()) {
        return
    }

    val content = javaScript.getChapterContent(details?.chapterList?.first()!!.url)
    if (content.isNullOrEmpty()) {
        println("校验失败")
        return
    } else {
        println("校验成功")
    }

}