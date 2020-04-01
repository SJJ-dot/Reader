package com.sjianjun.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sjianjun.reader.bean.JavaScript
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import sjj.alog.Log

@RunWith(AndroidJUnit4::class)
class JavaScriptTest {
    val javaScript = JavaScript()
    @Test
    fun test(): Unit = runBlocking {
        javaScript.search("黎明之剑").collect {
            Log.e(it)
        }
    }
}