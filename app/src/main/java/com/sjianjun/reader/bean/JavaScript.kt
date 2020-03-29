package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sjianjun.reader.rhino.ContextWrap

@Entity
class JavaScript {
    @PrimaryKey(autoGenerate = true)
    var id = 0
    /**
     * 来源 与书籍[Book.source]对应。例如：笔趣阁
     */

    var source = ""

    /**
     * js 脚本内容
     */
    var js = """
        fun doSearch(){
            //
        }
    """.trimIndent()


    fun inject(contextWrap: ContextWrap) {
        //绑定上下文。注入http db dao 等等
    }

}