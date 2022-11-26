package com.sjianjun.test

import com.sjianjun.test.bean.BookSource
import java.io.File
import kotlin.math.min

val test = File("./src/main/java/com/sjianjun/test/test.js").readText()


suspend fun main(args: Array<String>) {
    val javaScript by lazy {
        BookSource().apply {
            name = "测试脚本"
            js = test
        }
    }
    val resultList = javaScript.search("霍格沃茨之灰巫师")
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
        println("搜索结果》》》》》》》》》》》》》》》》》》》》》》》》》》》》》》")
        println("搜索到结果数量：${resultList.size}")
        println("详情》》》》》》》》》》》》》》》》》》》》》》》》》》》》》》")
        println("书名：${details.title}")
        println("作者：${details.author}")
        println("链接：${details.url}")
        println("简介：${details.intro}")
        println("封面：${details.cover}")
        println("章节内容》》》》》》》》》》》》》》》》》》》》》》》》》》》》》》长度：${content.length}")
        println(content.subSequence(0, min(100,content.length)))
        println("校验成功")
    }

}