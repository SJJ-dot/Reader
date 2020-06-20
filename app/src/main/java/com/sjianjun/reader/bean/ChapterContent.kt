package com.sjianjun.reader.bean

import android.text.SpannableStringBuilder
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sjianjun.reader.utils.html

@Entity
data class ChapterContent(
    /**
     * 章节内容主键应该和章节信息主键相同
     */
    @PrimaryKey
    var url: String = "",

    var bookUrl: String = "",

    var content: String? = null

) {
    fun format(): SpannableStringBuilder {
        val content = SpannableStringBuilder("$content".html())
        Regex("\\A\\s*").find(content)?.apply {
            content.replace(range.first,range.last+1,"")
        }
        content.insert(0,"\u3000\u3000")
        var result = Regex("\n+\\s*").find(content)
        while (result != null) {
            content.replace(result.range.first,result.range.last+1,"\n\u3000\u3000")
            result = result.next()
        }
        return content
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChapterContent

        if (url != other.url) return false
        if (bookUrl != other.bookUrl) return false
        if (content != other.content) return false

        return true
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + bookUrl.hashCode()
        result = 31 * result + (content?.hashCode() ?: 0)
        return result
    }
}