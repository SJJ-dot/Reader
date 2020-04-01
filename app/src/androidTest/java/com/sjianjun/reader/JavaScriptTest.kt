package com.sjianjun.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.bean.JavaScript.Func.doSearch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import sjj.alog.Log

@RunWith(AndroidJUnit4::class)
class JavaScriptTest {
    val javaScript = JavaScript("biquge5200") {
        """
            function doSearch(http,query){
                return http.get("https://www.biquge5200.cc/95_95192/");
            }
        """.trimIndent()
    }

    @Test
    fun testJavaScriptSearch(): Unit = runBlocking {
        val result = javaScript.execute<String>(doSearch, "黎明之剑")
        Log.e(result)
    }
}