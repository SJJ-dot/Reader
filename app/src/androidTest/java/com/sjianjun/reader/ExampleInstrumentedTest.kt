package com.sjianjun.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sjianjun.reader.http.client
import com.sjianjun.reader.http.http
import com.sjianjun.reader.rhino.js
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
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
    fun testJs() {
        js {
            val res = eval(
                """
                "aaaa。bbbbb".split("。")[1]
            """.trimIndent()
            )
            Log.e(res)
        }
    }

    @Test
    fun testRhinoImport() {

        js {
            val name = Jsoup::class.java.name
            val res = eval(
                """
                importClass(Packages.${name})
                
                encodeURIComponent
            """.trimIndent()
            )

            Log.e(res)
        }
    }

    @Test
    fun testGet() {
        val get = get("https://api.github.com/repos/SJJ-dot/Reader/releases/latest")
        Log.e(get)
    }

    @Test
    fun testPost() {
        val get = post("https://log.kxxsc.com/yd/exceptionlog/report")
        Log.e(get)
    }

    @Test
    fun testJava2Js() {
        js {
            val a = ATest()
            putProperty("aaa", javaToJS(a))
            val evaluateString = eval(
                """
                    aaa.test()
                """.trimIndent()
            )
            Log.e(evaluateString)
            Log.e(Context.toString(evaluateString))
        }


    }

    /**
     * 测试js调用Java http方法
     */
    @Test
    fun testJsCallJavaHttp() {
        js {
            putProperty("http", javaToJS(http))
            val evaluateString = eval(
                """
                    http.get("https://www.biquge5200.cc/95_95192/")
                """.trimIndent()
            )
            Log.e(evaluateString)
            Log.e(Context.toString(evaluateString))
        }

    }

    /**
     * 测试Js中调用Java方法 执行方法的是同一个对象。
     */
    @Test
    fun testJsCallJavaFun() {
        js {
            putProperty("javaObj", javaToJS(this@ExampleInstrumentedTest))
            val result = eval(
                """
                    javaObj.hello("js")
                """.trimIndent()
            )
            val jsResult = Context.jsToJava(result, String::class.java)
            assert(hello("java").toString() == jsResult)
        }
    }

    fun hello(from: String): Int {
        val id = System.identityHashCode(this)
        Log.e("this is from $from; id=${id}")
        return id
    }

    /**
     * 测试Java调用js方法
     */
    @Test
    fun testJavaCallJsFun() {
        js {
            putProperty("javaObj", javaToJS(this@ExampleInstrumentedTest))
            eval(
                """
                   function jsFun(value){
                    return "jsReturn > "+value
                   }
                   function jsFun2(value){
                                       return "jsReturn > "+value
                                      }
                """.trimIndent()
            )
            // one

            val result = eval("jsFun('evaluateString')+jsFun2('test')")

            Log.e(jsToJava<String>(result))

            //or
            val jsFun = get("jsFun")
            if (jsFun is Function) {
                val call = jsFun.call(context, scriptable, scriptable, arrayOf("get fun"))
                Log.e(call)
            } else {
                Log.e("not found js Function")
            }


        }
    }

    @Test
    fun testHtmlDom() {
        val get = get("https://www.biquge5200.cc/95_95192/")
        val doc = Jsoup.parse(get)

        js {
            putProperty("doc", javaToJS(doc))
            val evaluateString = eval(
                """
                    doc.getElementById("info")
                """.trimIndent()
            )
            Log.e(Context.jsToJava(evaluateString, Element::class.java))
            Log.e(Context.toString(evaluateString))
        }

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

    class ATest {
        fun test(): String {
            return "this is A"
        }
    }
}
