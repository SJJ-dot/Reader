package com.sjianjun.reader.repository

import androidx.annotation.WorkerThread
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.python.ScriptCallTargets
import com.sjianjun.reader.python.ScriptTaskRunner
import com.sjianjun.reader.repository.DbFactory.db
import com.sjianjun.reader.utils.MessageException
import com.sjianjun.reader.utils.name
import com.sjianjun.reader.utils.normalizedChapterName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log
import java.util.concurrent.ConcurrentHashMap
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
        //本次加载的标识：同一本书（同 URL）的重复加载只保留最新一次，
        //开始时会取消上一次还在跑的加载（脚本连接/线程一并终止），旧调用即使侥幸返回也不写库
        val target = ScriptCallTargets.detailsKey(book.url)
        val generation = ScriptCallTargets.begin(target)
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
            //本次调用已被更新的调用取代（或页面已退出）：结果直接丢弃，不写库、不改状态
            if (!ScriptCallTargets.isLatest(target, generation)) {
                Log.i("${book.title} 加载已被取代，丢弃本次结果")
                return@withIo
            }
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
                        //这里不传 taskKey：本调用嵌在详情加载内部，用独立标识，避免取消掉外面的加载
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
            //协程被取消（页面销毁、发起方主动取消）：恢复加载中状态，不当作加载失败
            Log.i("加载被取消：${book.title}")
            restoreLoadingState(book, target, generation)
            throw e
        } catch (e: Throwable) {
            if (ScriptTaskRunner.isCancelled(e)) {
                //脚本调用被取消（页面退出、或被同一本书更新的加载取代）：不算加载失败
                Log.i("${book.title} 加载被取消，忽略本次结果")
                restoreLoadingState(book, target, generation)
                //协程若已被取消，这里把取消继续向上抛；只是被取代则正常结束
                currentCoroutineContext().ensureActive()
            } else if (ScriptCallTargets.isLatest(target, generation)) {
                Log.e("加载书籍详情：$book", e)
                book.isLoading = false
                book.error = android.util.Log.getStackTraceString(e)
                bookDao.updateBook(book)
            } else {
                Log.i("${book.title} 加载已被取代，忽略本次结果")
            }
        } finally {
            ScriptCallTargets.end(target, generation)
        }
    }

    /**
     * 取消/被取代时恢复"加载中"状态：
     * - 只有仍是该目标最新一次调用时才写，避免把新加载刚置上的"加载中"提前清掉；
     * - 用 NonCancellable：协程已被取消时也要能写库。
     */
    private suspend fun restoreLoadingState(book: Book, target: String?, generation: Long) {
        if (!ScriptCallTargets.isLatest(target, generation)) return
        withContext(NonCancellable) {
            book.isLoading = false
            book.error = null
            bookDao.updateBook(book)
        }
    }


    /**
     * 章节正文加载锁：按「书 + 章节链接」维度串行化。
     *
     * 阅读页和朗读服务（预加载/切章）可能同时要同一章，加锁后后来者直接复用刚加载好的缓存，
     * 不会重复联网、也不会并发抓同一章。
     */
    private val chapterContentLocks = ConcurrentHashMap<String, Mutex>()

    private fun chapterContentLock(chapter: Chapter): Mutex {
        val key = "${chapter.bookId}|${chapter.url}"
        chapterContentLocks[key]?.let { return it }
        val created = Mutex()
        return chapterContentLocks.putIfAbsent(key, created) ?: created
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

        //同一章正文的重复加载只保留最新一次：这里先取消上一次请求，
        //等待章节锁的上一次调用因此会立刻结束并让出锁，不会让新请求干等它超时
        ScriptCallTargets.cancelCurrent(ScriptCallTargets.contentKey(chapter.url))
        chapterContentLock(chapter).withLock {
            // 等锁期间另一边可能已经加载好了：查一次库直接复用，避免重复抓取
            if (force != 1) {
                val cached = contentDao.getChapterContent(chapter.bookId, chapter.url).first().firstOrNull()
                if (cached != null && !cached.contentError) {
                    chapter.content = mutableListOf(cached)
                    chapter.isLoaded = true
                    Log.i("复用已加载的章节正文：${chapter.title}")
                    return@withIo chapter
                }
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
    }

    suspend fun getChapterContentPage(
        chapter: Chapter
    ): Boolean = withIo {
        chapterContentLock(chapter).withLock {
            // 锁内以数据库为准刷新分页：另一边可能刚加载了下一页，避免重复抓同一页
            val pages = contentDao.getChapterContent(chapter.bookId, chapter.url).first()
            if (pages.isNotEmpty()) {
                chapter.content = pages.toMutableList()
            }

            val lastContent = chapter.content?.lastOrNull() ?: return@withIo false
            val nextUrl = lastContent.nextPageUrl
            if (nextUrl.isNullOrBlank()) {
                return@withIo false
            }
            if (nextUrl.toHttpUrlOrNull() == null) {
                Log.i("${chapter.title} 加载下一页失败 url：$nextUrl")
                return@withIo false
            }
            Log.i("${chapter.title} 加载下一页 url：$nextUrl")
            val js = bookSourceDao.getBookSourceByBookId(chapter.bookId) ?: return@withIo false
            //同一页的重复请求只保留最新一次（py 内部按 url 生成标识）
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
        val oldBookId = readingRecord.bookId
        val chapter = chapterDao.getChapterByIndex(oldBookId, readingRecord.chapterIndex)
        //三级定位：精确同名就近 -> 同名就近 -> 用相邻章节锚点推断
        val readChapter = ChapterLocator.locateChapter(
            newBookId = book.id,
            oldBookId = oldBookId,
            oldIndex = chapter?.index ?: readingRecord.chapterIndex,
            oldTitle = chapter?.title
        ) ?: chapterDao.getChapterByIndex(book.id, readingRecord.chapterIndex)
            ?: chapterDao.getLastChapterByBookId(book.id)

        //定位到同一章节（序号相同或归一化同名）时保留页内偏移，否则清零，避免读到错误位置
        val keepOffset = readChapter != null && chapter != null &&
                (readChapter.index == chapter.index ||
                        chapter.title.orEmpty().normalizedChapterName() == readChapter.title.orEmpty().normalizedChapterName())
        readingRecord.bookId = book.id
        readingRecord.chapterIndex = readChapter?.index ?: readingRecord.chapterIndex
        if (!keepOffset) {
            readingRecord.offest = 0
            readingRecord.scrollOffset = 0
        }
        readingRecordDao.insertReadingRecord(readingRecord)
        EventBus.post(EventKey.BOOK_SOURCE_CHANGED,book.id)
    }
}
