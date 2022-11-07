package com.sjianjun.test.bean

//以书籍名、作者、来源 3者确定书籍的唯一性
class Book {
    var id: String = ""
        get() {
            if (field.isEmpty()) {
                field = url.trim()
            }
            return field
        }

    @JvmField
    var url: String = ""

    /**
     * 书籍来源[BookSource.source]
     */
    var source: String = ""
    @JvmField
    var bookSourceId: String = ""

    @JvmField
    var title: String = ""

    @JvmField
    var author: String = ""

    @JvmField
    var intro: String? = null

    @JvmField
    var cover: String? = null

    @JvmField
    var chapterList: List<Chapter>? = null

    override fun toString(): String {
        return "Book(url=$url, bookSourceId=$bookSourceId, title=$title, author=$author, intro=$intro, cover=$cover)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Book

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }


}