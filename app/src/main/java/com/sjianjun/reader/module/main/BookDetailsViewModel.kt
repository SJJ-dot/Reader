package com.sjianjun.reader.module.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sjianjun.coroutine.withIo
import com.sjianjun.coroutine.withMain
import com.sjianjun.reader.App
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.repository.BookUseCase
import com.sjianjun.reader.repository.DbFactory
import com.sjianjun.reader.utils.format
import com.sjianjun.reader.utils.launchIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import sjj.alog.Log
import java.io.File

class BookDetailsViewModel : ViewModel() {
    val bookLivedata = MutableLiveData<Book>()
    private var loadJob: Job? = null
    private val bookDao get() = DbFactory.db.bookDao()
    private val readingRecordDao get() = DbFactory.db.readingRecordDao()
    private val chapterDao get() = DbFactory.db.chapterDao()
    private val bookSourceDao get() = DbFactory.db.bookSourceDao()
    private val chapterContentDao get() = DbFactory.db.chapterContentDao()

    @OptIn(FlowPreview::class)
    fun init(title: String, autoRefresh: Boolean) {
        launchIo {
            var refresh = autoRefresh
            bookDao.getReadingBook(title).debounce(300).collectLatest {
                it?.also {
                    val record = readingRecordDao.getReadingRecord(it.title).firstOrNull()
                    val chapter = chapterDao.getChapterByIndex(record?.bookId ?: "", record?.chapterIndex ?: -1)
                    it.record = record
                    it.readChapter = chapter
                    it.bookSourceCount = bookDao.getBookBookSourceNum(it.title)
                    it.bookSource = bookSourceDao.getBookSourceById(it.bookSourceId)
                    it.lastChapter = chapterDao.getLastChapterByBookId(it.id)
                }
                bookLivedata.postValue(it)

                if (refresh) {
                    refresh = false
                    reloadBookFromNet(it)
                }
            }
        }
    }

    fun reloadBookFromNet(book: Book? = null) {
        val target = bookLivedata.value ?: book ?: return
        //取消上一次未完成的加载，避免重复加载
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            BookUseCase.reloadBookFromNet(target)
        }
    }

    /**
     * 页面销毁时取消正在进行的加载
     */
    fun cancelLoading() {
        loadJob?.cancel()
        loadJob = null
    }

    /**
     * 页面销毁时清理该书所有书源残留的加载中状态，
     * 避免加载被中断后 isLoading 一直为 true 导致书籍无法再次加载。
     *
     * 这里必须使用应用级作用域：本方法由 onDestroyView 调用，紧接着 ViewModel 就会被销毁，
     * viewModelScope 也随之取消，协程还没执行就被取消，重置不会生效
     */
    fun resetBookLoadingState(title: String) {
        App.app.appScope.launch(Dispatchers.IO) {
            runCatching { BookUseCase.resetBookLoadingStateByTitle(title) }
                .onFailure { Log.e("重置书籍加载状态失败：$title", it) }
        }
    }

    suspend fun setRecordToLastChapter() = withIo {
        val book = bookLivedata.value ?: return@withIo
        val lastChapter = book.lastChapter
        val readingRecord = readingRecordDao.getReadingRecord(book.title).firstOrNull() ?: ReadingRecord(book.title, book.id)
        if (readingRecord.chapterIndex != lastChapter?.index) {
            readingRecord.chapterIndex = lastChapter?.index ?: 0
            readingRecord.offest = 0
            readingRecord.scrollOffset = 0
            readingRecord.isEnd = false
            readingRecord.updateTime = System.currentTimeMillis()
            readingRecordDao.insertReadingRecord(readingRecord)
        }
    }

    /**
     * 导出书籍为 txt 文件。
     * @param startIndex 章节索引
     */
    fun exportBookToTxt(startIndex: Int, progress: (step: Int) -> Unit, onSuccess: (file: File) -> Unit, onError: (msg: String) -> Unit) {
        //导出当前书籍为txt文件
        Log.i("exportBookToTxt1")
        val book = bookLivedata.value
        if (book == null) {
            onError("书籍信息获取失败")
            return
        }
        Log.i("exportBookToTxt2")

        // run on IO
        launchIo {
            try {
                // choose export dir: externalCacheDir/export or cacheDir/export
                val baseDir = App.app.externalCacheDir ?: App.app.cacheDir
                val exportDir = File(baseDir, "export")
                if (exportDir.exists()) exportDir.deleteRecursively()
                if (!exportDir.exists()) exportDir.mkdirs()

                fun sanitize(fileName: String): String {
                    // forbid characters not allowed in filenames on many platforms
                    return fileName.replace(Regex("""[\\/:*?"<>|]"""), "_")
                }

                val author = book.author.takeIf { it.isNotBlank() } ?: "佚名"
                val name = sanitize("${book.title} - $author.txt")
                val outFile = File(exportDir, name)

                // get full chapter list
                val fullChapters = try {
                    chapterDao.getChapterListByBookId(book.id).first()
                } catch (_: Throwable) {
                    emptyList<com.sjianjun.reader.bean.Chapter>()
                }

                // filter chapters to export
                val chapters = if (startIndex <= 0) fullChapters else fullChapters.filter { it.index >= startIndex }

                outFile.bufferedWriter(Charsets.UTF_8).use { writer ->
                    // write book header
                    val displayAuthor = author
                    writer.appendLine(book.title)
                    writer.appendLine("作者：$displayAuthor")
                    val intro = book.intro.format(true)
                    writer.appendLine("简介：")
                    writer.appendLine(intro.ifBlank { "暂无简介" })
                    writer.appendLine("来源：${book.bookSource?.group} - ${book.bookSource?.name}")
                    writer.appendLine("网址：${book.url}")
                    writer.appendLine()

                    var step = 0
                    val total = chapters.size

                    if (total == 0) {
                        // nothing to write
                        writer.appendLine("[无章节可导出]")
                        withMain { progress(100) }
                    } else {
                        for (chapter in chapters) {
                            withMain { progress(step * 100 / total) }
                            step++
                            val titleLine = chapter.title ?: "第 ${chapter.index} 章"
                            writer.appendLine(titleLine)
                            writer.appendLine()

                            val contents = chapterContentDao.getChapterContent(book.id, chapter.index).first().sortedBy { it.pageIndex }
                            if (contents.isEmpty()) {
                                // if no cached content, leave blank or skip
                                writer.appendLine("[无内容]")
                                writer.appendLine()
                                continue
                            }

                            for (cc in contents) {
                                val text = cc.content.format().toString()
                                // ensure normalized line endings
                                writer.appendLine(text.replace("\r\n", "\n"))
                                writer.appendLine()
                            }

                            // add separator between chapters
                            writer.appendLine("\n")
                        }
                        withMain { progress(100) }
                    }
                }
                withMain {
                    onSuccess(outFile)
                }
            } catch (e: Throwable) {
                Log.e("exportBookToTxt", e)
                withMain {
                    onError(e.message ?: "导出失败")
                }
            }
        }
    }

}