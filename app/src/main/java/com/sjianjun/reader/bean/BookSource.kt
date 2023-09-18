package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.http.CookieMgr
import com.sjianjun.reader.http.http
import com.sjianjun.reader.rhino.ContextWrap
import com.sjianjun.reader.rhino.execute
import com.sjianjun.reader.rhino.importClassCode
import com.sjianjun.reader.rhino.js
import com.sjianjun.reader.utils.AesUtil
import okhttp3.HttpUrl
import org.jsoup.Jsoup
import org.jsoup.internal.StringUtil
import sjj.alog.Log

@Entity
class BookSource {

    @PrimaryKey
    var id: String = ""
        get() {
            if (field.isEmpty()) {
                field = "${group.trim()}:${name.trim()}"
            }
            return field
        }

    /**
     * 来源 与书籍[Book.source]对应。例如：笔趣阁
     */
    var name: String = ""

    /**
     * 分组名称
     */
    var group: String = ""

    /**
     * - js 脚本内容
     * - 多余的参数从arguments取
     */
    var js: String = ""
    var version: Int = -1
    var enable: Boolean = true
    var requestDelay: Long = 1000L

    /**
     * 书源校验结果
     */
    var checkResult: String? = null

    var checkErrorMsg: String? = null

    @Ignore
    val jsProps = mutableListOf<Pair<String, Any>>()

    /**
     * 书源管理页面是否被选中
     */
    @Ignore
    var selected = false

    /**
     * 脚本引擎语言
     */
    var lauanage: String = "js"


    inline fun <reified T> execute(func: Func, vararg params: String?): T? {
        Log.i("调用脚本方法：${func}")
        return execute(func.name, *params)
    }


    suspend fun search(query: String): List<SearchResult>? {
        return withIo {
            execute<List<SearchResult>>(Func.search, query)?.also {
                it.forEach {
                    it.bookSource = this@BookSource
                }
            }
        }
    }

    suspend fun getDetails(bookUrl: String): Book? {
        return withIo {
            execute<Book>(Func.getDetails, bookUrl)?.also {
                it.bookSourceId = id
            }
        }
    }

    suspend fun getChapterContent(chapterUrl: String): String? {
        return withIo {
            try {
                execute<String>(Func.getChapterContent, chapterUrl)
            } catch (t: Throwable) {
                Log.e("$name 加载章节内容出错：$chapterUrl", t)
                null
            }
        }
    }


    override fun toString(): String {
        return "BookSource(name='$name', group='$group', version=$version, enable=$enable)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BookSource

        if (id != other.id) return false
        if (name != other.name) return false
        if (group != other.group) return false
        if (js != other.js) return false
        if (version != other.version) return false
        if (enable != other.enable) return false
        if (requestDelay != other.requestDelay) return false
        if (checkResult != other.checkResult) return false
        if (checkErrorMsg != other.checkErrorMsg) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + group.hashCode()
        result = 31 * result + js.hashCode()
        result = 31 * result + version
        result = 31 * result + enable.hashCode()
        result = 31 * result + requestDelay.hashCode()
        result = 31 * result + (checkResult?.hashCode() ?: 0)
        result = 31 * result + (checkErrorMsg?.hashCode() ?: 0)
        return result
    }


    enum class Func {
        search, getDetails, getChapterContent
    }

}
