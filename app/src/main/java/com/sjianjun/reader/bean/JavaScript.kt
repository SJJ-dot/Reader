package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.sjianjun.reader.http.http
import com.sjianjun.reader.rhino.importClassCode
import com.sjianjun.reader.rhino.js
import com.sjianjun.reader.utils.withIo
import org.jsoup.Jsoup
import sjj.alog.Log

@Entity
data class JavaScript constructor(
    /**
     * 来源 与书籍[Book.source]对应。例如：笔趣阁
     */
    @PrimaryKey
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

    @Ignore
    @JvmField
    var headerScript = """
        ${importClassCode<Jsoup>()}
        ${importClassCode<Log>()}
        ${importClassCode<SearchResult>()}
        ${importClassCode<Chapter>()}
        ${importClassCode<Book>()}

        importClass(Packages.java.util.ArrayList)
        importClass(Packages.java.util.HashMap)
    """.trimIndent()

    inline fun <reified T> execute(func: Func, vararg params: String?): T? {
        return js {
            putProperty("http", javaToJS(http))
            evaluateString(headerScript)
            evaluateString(js)
            val paramList = params.filter { it?.isNotEmpty() == true }
            val result = if (paramList.isEmpty()) {
                evaluateString("${func.name}(http)")
            } else {
                val param = paramList.map { "\"$it\"" }.reduce { acc, s -> "$acc,$s" }
                evaluateString("${func.name}(http,${param})")
            }
            jsToJava<T>(result)
        }
    }

    suspend fun search(query: String): List<SearchResult>? {
        return withIo {
            execute<List<SearchResult>>(Func.search, query)
        }
    }

    suspend fun getDetails(bookUrl: String): Book? {
        return withIo {
            execute<Book>(Func.getDetails, bookUrl)
        }
    }

    suspend fun getChapterContent(chapterUrl: String): String? {
        return withIo {
            execute<String>(Func.getChapterContent, chapterUrl)
        }
    }

    enum class Func {
        search, getDetails, getChapterContent
    }

}