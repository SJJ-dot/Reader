package com.sjianjun.reader.python

import com.chaquo.python.Python
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.bean.BookSource.Func
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
import sjj.alog.Log


@PublishedApi internal val execModule by lazy { Python.getInstance().getModule("exec_script") }


/**
 * 调用Python书源脚本
 *
 * 每次调用都跑在一个脚本任务里：协程取消（页面销毁等）时该脚本调用立即终止。
 * 其中三类调用还要"同一目标只保留最新一次"（新的调用立即取消旧的），标识在这里统一推导：
 * - 搜索 → 书源（同一书源只保留最后一次搜索，不区分关键词）
 * - 加载详情 → 书籍 URL
 * - 加载章节内容 → 章节/分页 URL
 * 其他调用不参与去重（只随协程取消）。
 *
 * @param param2 第二个参数，仅 getDetails 使用（书籍最新章节链接，没有时传空串）
 */
suspend inline fun <reified T> BookSource.py(
    func: String,
    param: String?,
    param2: String? = null,
): T? {
    val exec = execModule
    val taskKey = when (func) {
        Func.search.name -> ScriptCallTargets.searchKey(id)
        Func.getDetails.name -> ScriptCallTargets.detailsKey(param.orEmpty())
        Func.getChapterContent.name -> ScriptCallTargets.contentKey(param.orEmpty())
        else -> null
    }
    val pyResult = ScriptTaskRunner.run(taskKey, js) {
        when {
            !param.isNullOrBlank() && param2 != null -> exec.callAttr("exec_script", js, func, param, param2)
            param.isNullOrBlank() -> exec.callAttr("exec_script", js, func)
            else -> exec.callAttr("exec_script", js, func, param)
        }
    }
    return when (T::class.java) {
        String::class.java -> pyResult.toString() as T
        Int::class.java -> pyResult.toInt() as T
        Boolean::class.java -> pyResult.toString().toBoolean() as T
        else -> {
            val json = pyResult.toString()
            try {
                gson.fromJson<T>(json)
            } catch (e: Exception) {
                Log.i("调用脚本方法：$func, 参数：$param, 返回结果：$json")
                throw e
            }
        }
    }
}