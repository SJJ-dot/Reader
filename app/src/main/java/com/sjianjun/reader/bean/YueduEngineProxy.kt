package com.sjianjun.reader.bean

import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.functions

/**
 * legado-engine（阅读书源引擎）的反射代理。
 * app 编译期不依赖 legado-engine：打包时包含该依赖则功能自动可用，
 * 不包含时编译不受影响，仅在使用 yuedu 书源时抛出友好错误。
 */
class YueduEngineProxy(rule: String) {

    private val instance: Any = try {
        Class.forName(IMPL_CLASS).getConstructor(String::class.java).newInstance(rule)
    } catch (e: ClassNotFoundException) {
        throw IllegalStateException("当前版本未集成阅读书源引擎(legado-engine)", e)
    }

    private val functions by lazy { instance::class.functions }

    private suspend fun call(name: String, vararg args: Any?): Any? {
        val fn = functions.first { it.name == name }
        return fn.callSuspend(instance, *args)
    }

    /** 校验书源是否有效：0 验证通过，1 验证失败，2 不支持验证 */
    suspend fun verify(query: String): Int = call("verify", query) as Int

    suspend fun getSiteUrl(): String = call("getSiteUrl") as String

    suspend fun search(query: String): String? = call("search", query) as String?

    suspend fun getDetails(bookUrl: String): String? = call("getDetails", bookUrl) as String?

    suspend fun getChapterContent(chapterUrl: String): String = call("getChapterContent", chapterUrl) as String

    private companion object {
        const val IMPL_CLASS = "sjj.legado.engine.YueduEngine"
    }
}
