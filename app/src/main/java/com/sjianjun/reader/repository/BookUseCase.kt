package com.sjianjun.reader.repository

import androidx.annotation.WorkerThread
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.repository.DbFactory.db
import com.sjianjun.reader.utils.MessageException
import com.sjianjun.reader.utils.name
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log
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

    /**
     * @param keepUpToIndex 增量更新时保留本地 index 小于等于该值的章节，其后的旧章节会被删除；
     *        为 null 时表示全量更新，删除本地全部章节后重新插入
     * chapterList 为 null 时表示章节没有变化，只更新书籍信息
     */
    suspend fun updateDetails(book: Book, keepUpToIndex: Int? = null) = withIo {
        db.runInTransaction {
            bookDao.updateBook(book)
            val chapterList = book.chapterList ?: return@runInTransaction
            if (keepUpToIndex == null) {
                chapterDao.deleteChapterByBookId(book.id)
            } else {
                chapterDao.deleteChapterAfterIndex(book.id, keepUpToIndex)
            }
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
            //章节链接为空的内容无法定位到章节
            val blankUrlContent = contentDao.cleanBlankUrlChapterContent()
            Log.i("清理脏数据 无章节链接的内容 $blankUrlContent")
            //章节被删除、章节链接变更后遗留的内容，留着会占用空间还可能被后续章节命中
            val orphanContent = contentDao.cleanOrphanChapterContent()
            Log.i("清理脏数据 无对应章节的内容 $orphanContent")
            //内容已被清理的章节要同步去掉已加载标记，避免读取到空内容
            val loadedChapter = chapterDao.cleanChapterLoadedState()
            Log.i("清理脏数据 无内容的已加载章节 $loadedChapter")
        }
    }

    /**
     * 重置全部书籍的加载状态。
     * 加载书籍详情时会把 isLoading 置为 true，若加载过程中被强制退出，状态会残留在数据库中，
     * 导致书架页和详情页一直处于加载中且无法再次加载。应用启动时统一清理为 false。
     */
    @WorkerThread
    fun resetBookLoadingState() {
        val count = bookDao.resetBookLoadingState()
        if (count > 0) {
            Log.i("重置书籍加载状态 数量：$count")
        }
    }

    /**
     * 重置指定书籍的加载状态，页面销毁时调用
     */
    @WorkerThread
    fun resetBookLoadingState(bookIds: List<String>) {
        if (bookIds.isEmpty()) return
        val count = bookDao.resetBookLoadingState(bookIds)
        if (count > 0) {
            Log.i("重置书籍加载状态 数量：$count books：$bookIds")
        }
    }

    /**
     * 重置同名书籍（所有书源）的加载状态，详情页销毁时调用
     */
    @WorkerThread
    fun resetBookLoadingStateByTitle(title: String) {
        val count = bookDao.resetBookLoadingStateByTitle(title)
        if (count > 0) {
            Log.i("重置书籍加载状态 数量：$count 书名：$title")
        }
    }

    /**
     * @param forceFull 强制加载完整章节列表（不把本地最新章节链接传给脚本），用于本地章节数据异常时重建
     */
    suspend fun reloadBookFromNet(book: Book?, forceFull: Boolean = false) = withIo {
        book ?: return@withIo
        try {
            if (book.bookSourceId == BookSource.fileImportBookSource.id){
                //本地导入的书籍所有内容都在本地
                book.isLoading = false
                book.error = null
                bookDao.updateBook(book)
                return@withIo
            }
            val script = bookSourceDao.getBookSourceById(book.bookSourceId) ?: throw MessageException("未找到对应书籍书源")
            book.isLoading = true
            bookDao.updateBook(book)
            //本地已有的最新章节链接，没有时传空串。脚本可据此只返回最新章节，加速检查更新
            val lastChapterUrl = if (forceFull) "" else chapterDao.getLastChapterByBookId(book.id)?.url ?: ""
            val bookDetails = script.getDetails(book.url, lastChapterUrl)!!
            if (bookDetails.title != book.title) {
                throw MessageException("加载失败：书籍标题发生变化")
            }
            //脚本返回的书籍id/url可能为空或与本地不一致，统一使用本地书籍标识，
            //避免章节按错误的bookId删除、书籍信息更新到错误的数据行
            bookDetails.id = book.id
            if (bookDetails.url.isBlank()) {
                bookDetails.url = book.url
            }
            val chapterList = bookDetails.chapterList!!
            val localChapters = chapterDao.getChapterListByBookId(book.id).first()
            var fullChapterList: List<Chapter> = localChapters
            var keepUpToIndex: Int? = null
            if (chapterList.isEmpty()) {
                //脚本返回空章节列表，视为本次没有更新，本地章节保持不变，只更新书籍信息
                Log.i("${book.title} 未检测到章节更新")
                //chapterList 为null时不修改本地章节
                bookDetails.chapterList = null
            } else {
                //脚本约定：章节列表升序返回（旧->新），且本地已有的章节以返回数据为准。
                //forceFull 时不定位，直接用返回的完整章节列表重建本地章节
                val firstReturnedIndex = if (forceFull) {
                    null
                } else {
                    //本地章节url -> index，重复url取最后出现的一个
                    val localIndexByUrl = HashMap<String, Int>(localChapters.size)
                    localChapters.forEach { localIndexByUrl[it.url] = it.index }
                    //用返回的第一个章节在本地定位
                    localIndexByUrl[chapterList.first().url]
                }
                if (firstReturnedIndex != null) {
                    //定位成功：定位位置之前的本地章节保留，
                    //定位位置及其之后的本地章节一律以返回数据为准，整体用返回章节覆盖
                    var nextIndex = firstReturnedIndex
                    chapterList.forEach { chapter ->
                        chapter.bookId = book.id
                        chapter.index = nextIndex++
                    }
                    fullChapterList = localChapters.filter { it.index < firstReturnedIndex } + chapterList
                    //定位位置及其之后的本地章节全部丢弃
                    keepUpToIndex = firstReturnedIndex - 1
                    Log.i("${book.title} 增量加载章节：首章定位到第${firstReturnedIndex}章，覆盖${chapterList.size}章")
                } else {
                    //第一个章节本地查询不到（或强制全量），按全量更新：
                    //以返回的章节列表为准重建本地章节（keepUpToIndex 保持 null）
                    chapterList.forEachIndexed { index, chapter ->
                        chapter.bookId = book.id
                        chapter.index = index
                    }
                    fullChapterList = chapterList
                    Log.i("${book.title} 按全量更新章节：共${chapterList.size}章")
                }
                //keepUpToIndex 为 null 时表示全量替换本地章节
                bookDetails.chapterList = fullChapterList
            }
            book.chapterList = fullChapterList
            book.isLoading = false
            book.error = null
            if (bookDetails.chapterList != null) {
                //先检查章节内容是否有错
                val record = readingRecordDao.getReadingRecord(book.title).firstOrNull()
                val readingChapter = chapterDao.getChapterByIndex(record?.bookId ?: "", record?.chapterIndex ?: -1)
                val chapterLikeName = readingChapter?.let {
                    chapterDao.getChapterLikeName(book.id, readingChapter.name()).minByOrNull { abs(readingChapter.index - it.index) }
                }
                val content = contentDao.getChapterContent(book.id, chapterLikeName?.url ?: "").first().firstOrNull()
                if (content?.contentError == true) {
                    val chapter = fullChapterList.firstOrNull { it.url == content.chapterUrl }
                    if (chapter != null) {
                        chapter.content = mutableListOf(content)
                        val content = script.getChapterContent(chapter.url)
                        content.bookId = chapter.bookId
                        content.chapterUrl = chapter.url
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
                }
            }
            updateDetails(bookDetails, keepUpToIndex)
        } catch (e: CancellationException) {
            //加载被取消（例如退出详情页），只清除加载中状态，不当作加载失败记录错误
            Log.i("加载被取消：${book.title}")
            book.isLoading = false
            book.error = null
            bookDao.updateBook(book)
            throw e
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
            val chapterContent = contentDao.getChapterContent(chapter.bookId, chapter.url).first()
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
        content.chapterUrl = chapter.url
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
        content.chapterUrl = chapter.url
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
            readingRecord.scrollOffset = 0
        }
        readingRecordDao.insertReadingRecord(readingRecord)
        EventBus.post(EventKey.BOOK_SOURCE_CHANGED,book.id)
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
