package com.sjianjun.reader.utils

import com.sjianjun.reader.bean.Book

val bookComparator = BookComparator()

class BookComparator : Comparator<Book> {
    override fun compare(o1: Book, o2: Book): Int {
        val updateTime = o2.record?.updateTime?.let {
            o1.record?.updateTime?.compareTo(it)
        }
        if (updateTime != null && updateTime != 0) {
            return -updateTime
        }
        val compareTo = o1.unreadChapterCount.compareTo(o2.unreadChapterCount)
        if (compareTo != 0) {
            return -compareTo
        }


        if (o1.error != null || o2.error != null) {
            return if (o1.error != null) -1 else 1
        }

        val compareTitle = o1.title.compareTo(o2.title)
        if (compareTitle != 0) {
            return -compareTitle
        }

        return o1.bookSourceId.compareTo(o2.bookSourceId)
    }
}