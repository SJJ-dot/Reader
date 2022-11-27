package com.sjianjun.test

import com.sjianjun.test.bean.BookSource
import com.sjianjun.test.bean.SearchResult
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.min

suspend fun test(name: String, script: String, query: String): List<SearchResult> {
    val javaScript by lazy {
        BookSource().apply {
            this.name = name
            js = script
        }
    }
    val resultList = javaScript.search(query)
    println("搜索到结果数量：${resultList?.size}")
    if (resultList.isNullOrEmpty()) {
        return emptyList()
    }
    val selectBook = resultList[0]
    val ta = "${selectBook.bookTitle}_${selectBook.bookAuthor}"
    var details = javaScript.getDetails(selectBook.bookUrl)
    println("搜索到章节数量：${details?.chapterList?.size}")
    if (details?.chapterList.isNullOrEmpty()) {
        return emptyList()
    }
    if (ta != "${details!!.title}_${details.author}") {
        println("搜索结果书名与详情页不同1：${ta}==>${details.title}_${details.author}")
        return emptyList()
    }

    details = javaScript.getDetails(details!!.url)
    println("详情页再次加载搜索到章节数量：${details?.chapterList?.size}")
    if (details?.chapterList.isNullOrEmpty()) {
        return emptyList()
    }
    if (ta != "${details!!.title}_${details.author}") {
        println("搜索结果书名与详情页不同2：${ta}==>${details.title}_${details.author}")
        return emptyList()
    }

    val content = javaScript.getChapterContent(details?.chapterList?.first()!!.url)
    if (content.isNullOrEmpty()) {
        println("校验失败")
        return emptyList()
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
        println(content.subSequence(0, min(100, content.length)))
        println("校验成功")
        return resultList
    }
}

suspend fun testAll() = withContext(Dispatchers.IO) {
    val list = File("BookSource/js").listFiles().map {
        async {
            val resultList = test(it.nameWithoutExtension, it.readText(), "我的")
            if (resultList.isEmpty()) {
                "书源：${it.nameWithoutExtension}在第一轮搜索时失败"
            } else {
                delay(30 * 1000)
                val result = resultList.sortedBy { it.bookTitle.length }.last()
                val results = test(it.nameWithoutExtension, it.readText(), result.bookTitle)
                if (results.isEmpty()) {
                    "书源：${it.nameWithoutExtension}在第二轮搜索：${result.bookTitle}时失败"
                } else {
                    null
                }
            }
        }
    }.awaitAll().filterNotNull()
    if (list.isEmpty()) {
        println("脚本校验全部成功")
    } else {
        list.forEach {
            println(it)
        }
    }
}

fun main(args: Array<String>) = runBlocking {
//    val test = File("./src/main/java/com/sjianjun/test/test.js").readText()
//    test("测试脚本", test, "霍格沃茨之灰巫师")

    testAll()
}