@file:Suppress("unused")

package io.legado.app.help.book

import io.legado.app.constant.BookSourceType
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import java.time.LocalDate
import java.time.Period.between
import kotlin.math.max
import kotlin.math.min


val Book.isWebFile: Boolean
    get() = isType(BookType.webFile)


fun Book.addType(@BookType.Type vararg types: Int) {
    types.forEach {
        type = type or it
    }
}

fun Book.removeType(@BookType.Type vararg types: Int) {
    types.forEach {
        type = type and it.inv()
    }
}

fun Book.removeAllBookType() {
    removeType(BookType.allBookType)
}

fun Book.isType(@BookType.Type bookType: Int): Boolean = type and bookType > 0

fun Book.upType() {
    if (type < 8) {
        type = when (type) {
            BookSourceType.image -> BookType.image
            BookSourceType.audio -> BookType.audio
            BookSourceType.file -> BookType.webFile
            else -> BookType.text
        }
        if (origin == BookType.localTag || origin.startsWith(BookType.webDavTag)) {
            type = type or BookType.local
        }
    }
}

// 根据当前日期计算章节总数
fun Book.simulatedTotalChapterNum(): Int {
    return if (readSimulating()) {
        val currentDate = LocalDate.now()
        val daysPassed = between(config.startDate, currentDate).days + 1
        // 计算当前应该解锁到哪一章
        val chaptersToUnlock =
            max(0, (config.startChapter ?: 0) + (daysPassed * config.dailyChapters))
        min(totalChapterNum, chaptersToUnlock)
    } else {
        totalChapterNum
    }
}

fun Book.readSimulating(): Boolean {
    return config.readSimulating
}