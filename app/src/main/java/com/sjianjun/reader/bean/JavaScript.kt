package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sjianjun.reader.rhino.ContextWrap
import com.sjianjun.reader.rhino.js
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import sjj.alog.Log

@Entity
data class JavaScript(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,
    /**
     * 来源 与书籍[Book.source]对应。例如：笔趣阁
     */

    var source: String = "",

    /**
     * js 脚本内容
     */
    var js: String = """
        fun doSearch(){
            //
        }
    """.trimIndent()
) {


    fun inject(contextWrap: ContextWrap) {
        //绑定上下文。注入http db dao 等等
    }

    suspend fun search(query: String): Flow<String> {
        return flow {
            //todo
           emit(query)
        }.flowOn(Dispatchers.IO)
    }

}