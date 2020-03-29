package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.PrimaryKey

//以书籍名、作者、来源 3者确定书籍的唯一性
@Entity
class Book {
    @PrimaryKey(autoGenerate = true)
    var id:Int = 0
    var title: String = ""
    var author: String = ""
    /**
     * 书籍来源[JavaScript.source]
     */
    var source: String = ""
}