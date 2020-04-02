package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.sjianjun.reader.http.http
import com.sjianjun.reader.rhino.importClassCode
import com.sjianjun.reader.rhino.js
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.jsoup.Jsoup
import sjj.alog.Log

@Entity
data class JavaScript constructor(
    /**
     * 来源 与书籍[Book.source]对应。例如：笔趣阁
     */
    @JvmField
    var source: String = "",

    /**
     * - js 脚本内容
     * - 多余的参数从arguments取
     */
    @JvmField
    var js: String = ""
) {

    constructor(source: String, js: () -> String) : this(source = source, js = js())

    @PrimaryKey(autoGenerate = true)
    @JvmField
    var id: Int = 0

    @Ignore
    @JvmField
    var headerScript = """
        ${importClassCode<Jsoup>()}
        ${importClassCode<Log>()}
        ${importClassCode<SearchResult>()}
        ${importClassCode<Chapter>()}
        ${importClassCode<Book>()}

        importClass(Packages.java.util.ArrayList)
    """.trimIndent()

    @Throws(Exception::class)
    inline fun <reified T> execute(func: Func, vararg params: String): T? {
        return js {
            putProperty("http", javaToJS(http))
            evaluateString(headerScript)
            evaluateString(js)
            val result = if (params.isEmpty()) {
                evaluateString("${func.name}(http)")
            } else {
                val param = params.map { "\"$it\"" }.reduce { acc, s -> "$acc,$s" }
                evaluateString("${func.name}(http,${param})")
            }
            jsToJava<T>(result)
        }
    }

    @Throws(Exception::class)
    suspend fun search(query: String): Flow<List<SearchResult>> {
        return flow {
            try {
                val result = execute<List<SearchResult>>(Func.search, query)
                emit(result!!)
            } catch (e: Exception) {
                emit(emptyList())
            }
        }.flowOn(Dispatchers.IO)
    }

    enum class Func {
        search, getDetails, getChapterContent
    }

}