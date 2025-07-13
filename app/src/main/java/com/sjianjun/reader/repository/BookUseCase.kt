package com.sjianjun.reader.repository

import androidx.annotation.WorkerThread
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.repository.DbFactory.db
import com.sjianjun.reader.utils.MessageException
import com.sjianjun.reader.utils.name
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log
import kotlin.collections.firstOrNull
import kotlin.math.abs

object BookUseCase {
    private val contentDao get() = db.chapterContentDao()
    private val bookDao get() = db.bookDao()
    private val chapterDao get() = db.chapterDao()
    private val readingRecordDao get() = db.readingRecordDao()
    private val bookSourceDao get() = db.bookSourceDao()

    suspend fun delete(book: Book) = withIo {
        db.runInTransaction {
            val ccnum = contentDao.deleteChapterContentByBookTitleAndAuthor(book.title)
            Log.i("删除内容数量：$ccnum")
            val bookNum = bookDao.deleteBook(book.title)
            Log.i("删除书籍数量：$bookNum")
            val chapterNum = chapterDao.deleteChapterByBookTitleAndAuthor(book.title)
            Log.i("删除章节数量：$chapterNum")
            val rn = readingRecordDao.deleteReadingRecord(book.title)
            Log.i("删除阅读记录数量：$rn")
            cleanDirtyData()
        }
    }

    suspend fun deleteById(book: Book) = withIo {
        val readingRecord = readingRecordDao.getReadingRecord(book.title).firstOrNull()
        if (readingRecord?.bookId == book.id) {
            bookDao.getBookByTitleAndAuthor(book.title).first().find { it.id != book.id }?.let { otherBook ->
                changeReadingRecordBookSource(otherBook)
            }
        }

        db.runInTransaction {
            contentDao.deleteChapterContentByBookId(book.id)
            bookDao.deleteBookById(book.id)
            chapterDao.deleteChapterByBookId(book.id)
        }
    }

    suspend fun updateDetails(book: Book) = withIo {
        db.runInTransaction {
            bookDao.updateBook(book)
            chapterDao.deleteChapterByBookId(book.id)
            val chapterList = book.chapterList ?: return@runInTransaction
            chapterDao.insertChapter(chapterList)
            chapterDao.markChaptersAsLoadedByBookId(book.id)
        }

    }

    @WorkerThread
    fun cleanDirtyData() {
        db.runInTransaction {
            val readingRecord = readingRecordDao.cleanReadingBook()
            Log.i("清理脏数据 readingRecord $readingRecord")
            val book = bookDao.cleanBook()
            Log.i("清理脏数据 book $book")
            val chapter = chapterDao.cleanChapter()
            Log.i("清理脏数据 chapter $chapter")
            val content = contentDao.cleanChapterContent()
            Log.i("清理脏数据 content $content")
        }
    }

    suspend fun reloadBookFromNet(book: Book?) = withIo {
        book ?: return@withIo
        try {
            val script = bookSourceDao.getBookSourceById(book.bookSourceId) ?: throw MessageException("未找到对应书籍书源")
            book.isLoading = true
            bookDao.updateBook(book)
            val bookDetails = script.getDetails(book.url)!!
            val chapterList = bookDetails.chapterList!!
            chapterList.forEachIndexed { index, chapter ->
                chapter.bookId = book.id
                chapter.index = index
            }
            book.chapterList = chapterList
            book.isLoading = false
            book.error = null
            //先检查章节内容是否有错
            val record = readingRecordDao.getReadingRecord(book.title).firstOrNull()
            val readingChapter = chapterDao.getChapterByIndex(record?.bookId ?: "", record?.chapterIndex ?: -1)
            val chapterLikeName = readingChapter?.let {
                chapterDao.getChapterLikeName(book.id, readingChapter.name()).minByOrNull { abs(readingChapter.index - it.index) }
            }
            val content = contentDao.getChapterContent(book.id, chapterLikeName?.index ?: -1).firstOrNull()
            if (content?.contentError == true) {
                val chapter = chapterList[content.chapterIndex]
                chapter.content = mutableListOf(content)
                val content = script.getChapterContent(chapter.url)
                content.bookId = chapter.bookId
                content.chapterIndex = chapter.index
                if (!content.contentError) {
                    if (chapter.content?.firstOrNull()?.contentError == true && chapter.content?.firstOrNull()?.content == content.content) {
                        content.contentError = true
                    }
                }
                chapter.content = mutableListOf(content)
                chapter.isLoaded = true
                chapterDao.insert(chapter)
                contentDao.insert(content)
            }
            updateDetails(bookDetails)
        } catch (e: Throwable) {
            Log.e("加载书籍详情：$book", e)
            book.isLoading = false
            book.error = android.util.Log.getStackTraceString(e)
            bookDao.updateBook(book)
        }
    }


    /**
     * -1 local ,0 normal,1 force
     */
    suspend fun getChapterContent(chapter: Chapter, force: Int = 0): Chapter = withIo {
        if (chapter.isLoaded) {
            val chapterContent = contentDao.getChapterContent(chapter.bookId, chapter.index)
            chapter.content = chapterContent.toMutableList()
            if (force != 1 && chapter.content?.firstOrNull()?.contentError == false) {
                return@withIo chapter
            }
        }

        if (force == -1) {
            return@withIo chapter
        }

        val js = bookSourceDao.getBookSourceByBookId(chapter.bookId) ?: return@withIo chapter

        val content = js.getChapterContent(chapter.url)
        content.bookId = chapter.bookId
        content.chapterIndex = chapter.index
        if (!content.contentError) {
            if (chapter.content?.firstOrNull()?.contentError == true && chapter.content?.firstOrNull()?.content == content.content) {
                content.contentError = true
            }
        }
        chapter.content = mutableListOf(content)
        chapter.isLoaded = true
        chapterDao.insert(chapter)
        contentDao.insert(content)


        return@withIo chapter
    }

    suspend fun getChapterContentPage(
        chapter: Chapter
    ): Boolean = withIo {
        if (chapter.content?.lastOrNull()?.nextPageUrl.isNullOrBlank()) {
            return@withIo false
        }

        val lastContent = chapter.content?.lastOrNull()
        val nextUrl = lastContent?.nextPageUrl ?: return@withIo false
        if (nextUrl.toHttpUrlOrNull() == null) {
            Log.i("${chapter.title} 加载下一页失败 url：$nextUrl")
            return@withIo false
        }
        Log.i("${chapter.title} 加载下一页 url：$nextUrl")
        val js = bookSourceDao.getBookSourceByBookId(chapter.bookId) ?: return@withIo false
        val content = js.getChapterContent(nextUrl)
        content.bookId = chapter.bookId
        content.chapterIndex = chapter.index
        content.pageIndex = lastContent.pageIndex + 1
        chapter.content?.add(content)
        if (content.contentError) {
            Log.i("${chapter.title} 加载下一页失败 url：$nextUrl")
            return@withIo false
        }
        contentDao.insert(content)
        return@withIo true
    }

    /**
     * 切换正在阅读的书的书源
     */
    suspend fun changeReadingRecordBookSource(book: Book) = withIo {
        val readingRecord = readingRecordDao.getReadingRecord(book.title).firstOrNull() ?: ReadingRecord(book.title)
        if (readingRecord.bookId == book.id) {
            return@withIo
        }
        val lastChapter = chapterDao.getLastChapterByBookId(book.id)
        if (lastChapter == null) {
            //获取最新章节失败，尝试重新加载书籍，如果还是加载失败就算了。不管怎样换源必须成功
            BookUseCase.reloadBookFromNet(book)
        }
        val chapter = chapterDao.getChapterByIndex(readingRecord.bookId, readingRecord.chapterIndex)
        var readChapter: Chapter? = null
        if (chapter != null) {
            //根据章节名查询。取索引最接近那个 这里将章节名转拼音按相似度排序在模糊搜索的时候更准确
            readChapter = chapterDao.getChapterByTitle(book.id, chapter.title ?: "").minByOrNull { abs(chapter.index - it.index) }
            if (readChapter == null) {
                readChapter = getChapterLikeTitle(book.id, chapter.name()).firstOrNull()
            }
            if (readChapter == null) {
                readChapter = chapterDao.getChapterByIndex(book.id, chapter.index) ?: chapterDao.getLastChapterByBookId(book.id)
            }
        }
        readingRecord.bookId = book.id
        readingRecord.chapterIndex = readChapter?.index ?: readingRecord.chapterIndex
        if (readingRecord.chapterIndex == -1) {
            readingRecord.offest = 0
        }
        readingRecordDao.insertReadingRecord(readingRecord)
    }


    // 查询所有关键词的章节并取交集
    fun getChapterLikeTitle(bookId: String, chapterTitle: String): List<Chapter> {
        val keywords = Regex("[\\u4e00-\\u9fa5\\d]+").findAll(chapterTitle).map { it.value }.toList()
        if (keywords.isEmpty()) return emptyList()
        // 先查第一个关键词
        var result = chapterDao.getChapterLikeName(bookId, "%${keywords[0]}%").toSet()
        // 依次与后续关键词结果取交集
        for (i in 1 until keywords.size) {
            val chapters = chapterDao.getChapterLikeName(bookId, "%${keywords[i]}%")
            result = result.intersect(chapters.toSet())
        }
        return result.toList()
    }
}