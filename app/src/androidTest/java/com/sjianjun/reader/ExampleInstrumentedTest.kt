package com.sjianjun.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sjianjun.reader.bean.ATest
import com.sjianjun.reader.http.client
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import sjj.alog.Log

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.sjianjun.reader", appContext.packageName)
    }

    @Test
    fun testGet() {
        val get = get("https://www.biquge5200.cc/95_95192/")
        Log.e(get)
    }

    @Test
    fun testPost() {
        val get = post("https://log.kxxsc.com/yd/exceptionlog/report")
        Log.e(get)
    }

    @Test
    fun testJava2Js() {
        val context = Context.enter()
        //android 使用 Dalvik 所以不能优化为class 字节码
        context.optimizationLevel = -1
        val scriptable = context.initStandardObjects()

        val a = ATest()
        val jsA = Context.javaToJS(a, scriptable)
        ScriptableObject.putProperty(scriptable, "aaa", jsA)

        val evaluateString = context.evaluateString(scriptable, """
                    aaa.test()
                """.trimIndent(), null, 0, null)
        Log.e(evaluateString)
        Log.e(Context.toString(evaluateString))
        Context.exit()
    }

    @Test
    fun testJsCallJavaFun() {
        val context = Context.enter()
        //android 使用 Dalvik 所以不能优化为class 字节码
        context.optimizationLevel = -1
        val scriptable = context.initStandardObjects()

        val http = client
        val jsHttp = Context.javaToJS(http, scriptable)
        ScriptableObject.putProperty(scriptable, "http", jsHttp)

        val evaluateString = context.evaluateString(scriptable, """
                    http.get("https://www.biquge5200.cc/95_95192/")
                """.trimIndent(), null, 0, null)
        Log.e(evaluateString)
        Log.e(Context.toString(evaluateString))
        Context.exit()
    }

    @Test
    fun testJavaCallJsFun() {

    }

    @Test
    fun testHtmlDom() {
        val get = get("https://www.biquge5200.cc/95_95192/")
        val doc = Jsoup.parse(get)


        val context = Context.enter()
        //android 使用 Dalvik 所以不能优化为class 字节码
        context.optimizationLevel = -1
        val scriptable = context.initStandardObjects()

        val jsDoc = Context.javaToJS(doc, scriptable)
        ScriptableObject.putProperty(scriptable, "doc", jsDoc)

        val evaluateString = context.evaluateString(scriptable, """
                    doc.getElementById("info")
                """.trimIndent(), null, 0, null)
        Log.e(Context.jsToJava(evaluateString, Element::class.java))
        Log.e(Context.toString(evaluateString))
        Context.exit()
    }


    fun get(
        url: String = "",
        header: Map<String, String> = emptyMap(),
        queryMap: Map<String, String> = emptyMap()
    ) = runBlocking {
        client.get<String>(url, queryMap, header)
    }

    fun post(
        url: String = "",
        header: Map<String, String> = emptyMap(),
        fieldMap: Map<String, String> = emptyMap()
    ) = runBlocking {
        client.post<String>(url, fieldMap, header)
    }

}
