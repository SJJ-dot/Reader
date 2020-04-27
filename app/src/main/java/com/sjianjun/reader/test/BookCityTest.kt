package com.sjianjun.reader.test

import com.sjianjun.reader.bean.Page
import com.sjianjun.reader.http.http
import com.sjianjun.reader.utils.JS_SOURCE_QI_DIAN
import org.jsoup.Jsoup
import sjj.alog.Log

object BookCityTest {
    var test = false
    private val source: String = JS_SOURCE_QI_DIAN
    fun test() {
        if (!test) {
            return
        }
        Log.e(getPageList())
    }


    private fun getPageList(): Page? {
        val http = http
        val page = Page()
        val pageList = ArrayList<Page>()
        val bookGroupList = ArrayList<Page.BookGroup>()
        page.pageList = pageList
        page.bookGroupList = bookGroupList

        val baseUrl = "https://www.qidian.com/"
        val document = Jsoup.parse(http.get(baseUrl), baseUrl)
        val classifyListEl = document.select("#classify-list a")
        for (i in classifyListEl.indices) {
            val classifyEl = classifyListEl[i]
            val classifyPage = Page()
            classifyPage.source = source
            classifyPage.title = classifyEl.select(".info i").text()
            classifyEl.absUrl("href")
            classifyPage.pageScript = """
                getClassify("$http");
            """.trimIndent()
            pageList.add(classifyPage)
        }

        return page
    }
}