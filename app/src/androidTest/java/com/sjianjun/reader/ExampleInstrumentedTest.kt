package com.sjianjun.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sjianjun.reader.http.HttpInterface
import com.sjianjun.reader.http.createRetrofit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

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
        log(get)
    }

    @Test
    fun testPost() {
        val get = post("https://log.kxxsc.com/yd/exceptionlog/report")
        log(get)
    }

    fun get(
        url: String = "",
        header: Map<String, String> = emptyMap(),
        queryMap: Map<String, String> = emptyMap()
    ) = runBlocking {
        createRetrofit().create(HttpInterface::class.java).get(url, header, queryMap).await()
    }

    fun post(
        url: String = "",
        header: Map<String, String> = emptyMap(),
        fieldMap: Map<String, String> = emptyMap()
    ) = runBlocking {
        createRetrofit().create(HttpInterface::class.java).post(url, header, fieldMap).await()
    }

    private fun log(msg: String) {
        println("ExampleInstrumentedTest $msg")
    }
}
