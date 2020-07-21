package com.sjianjun.reader.module.reader.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.sjianjun.charset.CharsetDetector
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ChapterContent
import com.sjianjun.reader.coroutine.launchIo
import com.sjianjun.reader.coroutine.withIo
import com.sjianjun.reader.coroutine.withMain
import com.sjianjun.reader.module.main.MainActivity
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.BOOK_SOURCE_FILE_IMPORT
import com.sjianjun.reader.utils.startActivity
import com.sjianjun.reader.utils.toast
import kotlinx.android.synthetic.main.reader_activity_file_import.*
import sjj.alog.Log
import java.io.*
import java.lang.StringBuilder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

/*
 * Created by shen jian jun on 2020-07-20
 */
class FileImportActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.reader_activity_file_import)
        progress_bar.show()
        val intent: Intent = intent
        val uri: Uri? = intent.getData()
        readTxt(uri)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val intent: Intent = intent ?: return
        val uri: Uri? = intent.getData()
        readTxt(uri)
    }

    private val refresh = AtomicBoolean()
    private var count = 0
    private fun postLine(count: Int) {
        this.count = count
        if (refresh.compareAndSet(false, true)) {
            chapter_count.post {
                refresh.lazySet(false)
                chapter_count.text = "$count"
            }
        }
    }

    private fun readTxt(uri: Uri?) {
        Log.e("uri:$uri")
        launchIo {

            try {
                withMain { status.text = "读取书籍信息" }
                val book = readBookInfo(uri!!)
                withMain { status.text = "保存书籍信息" }
                DataManager.insertBookAndSaveReadingRecord(listOf(book.book))
                //保存章节信息
                val chapterList = mutableListOf<Chapter>()
                //单章最大字数
                val CHAPTER_CONTENT_MAX_LENGTH = 10000
                withMain { status.text = "解析并保存单章内容" }
                contentResolver.openInputStream(uri).use {
                    val reader = BufferedReader(InputStreamReader(it, book.charset))

                    val chapterContent = StringBuilder()
                    var chapterName: String? = book.book.title
                    var countLine = 0

                    var line: String? = reader.readLine()

                    while (line != null) {
                        postLine(++countLine)

                        if (line.isNullOrBlank()) {
                            line = reader.readLine()
                            continue
                        }

                        val lastChapterName = chapterName
                        val matchName = match(line, chapterNamePattern)


                        //章节内容不为空 并且匹配到章节名或者章节字数超过限制
                        if (chapterContent.isNotEmpty() && (!matchName.isNullOrBlank() || chapterContent.length > CHAPTER_CONTENT_MAX_LENGTH)) {

                            if (!matchName.isNullOrBlank()) {
                                chapterName = matchName
                            } else {
                                chapterContent.append(line)
                                chapterContent.append("<br/>")
                            }

                            createChapter(
                                book,
                                chapterList,
                                lastChapterName,
                                chapterContent.toString()
                            )

                            chapterContent.clear()
                        } else {
                            chapterContent.append(line)
                            chapterContent.append("<br/>")
                        }
                        line = reader.readLine()
                    }

                    if (chapterContent.isNotEmpty()) {
                        createChapter(book, chapterList, chapterName, chapterContent.toString())
                    }
                }
                withMain { status.text = "章节解析完成，更新书籍信息……" }
                book.book.chapterList = chapterList
                DataManager.updateBookDetails(book.book)
                withMain {
                    //导入小说创建成功
                    //
                    startActivity<MainActivity>()
                    toast("小说导入成功")
                    finish()
                }
            } catch (e: Throwable) {
                Log.e("小说导入失败", e)
                toast("小说导入失败")
                withMain {
                    finish()
                }
            }

        }
    }

    private suspend fun createChapter(
        book: TxtBook,
        chapterList: MutableList<Chapter>,
        chapterName: String?,
        chapterContent: String
    ) {
        val chapter = Chapter()
        chapter.url = "${book.book.url}_${chapterList.size}"
        chapter.bookUrl = book.book.url
        chapter.title = if (chapterName.isNullOrBlank())
            "第${chapterList.size + 1}章 ${book.book.title}"
        else
            chapterName

        chapter.index = chapterList.size
        chapterList.add(chapter)
//        chapter.title = "第${chapterList.size}章 ${chapter.title}"

        DataManager.insertChapterContent(
            ChapterContent(
                chapter.url,
                chapter.bookUrl,
                chapterContent
            )
        )
    }

    private suspend fun readBookInfo(uri: Uri): TxtBook = withIo {
        val book = contentResolver.openInputStream(uri).use {
            var fileName = uri.lastPathSegment
            val index = fileName?.indexOf(".")
            if (index != null && index > 0) {
                fileName = fileName?.substring(0, index)
            }
            withMain { status.text = "解析文件字符编码" }
            val txtBook = TxtBook()
            txtBook.charset = CharsetDetector.detectCharset(it)
            txtBook.book.title = fileName ?: ""
            txtBook.book.url = uri.toString()
            txtBook
        }

        withMain { status.text = "解析书籍信息" }

        contentResolver.openInputStream(uri).use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream, book.charset))
            var matchTitle = book.book.title.isNotEmpty()
            var matchAuthor = false
            var matchIntro = false
            val sb = StringBuilder()
            var times = 0
            while (++times < 30) {
                val readLine = reader.readLine()
                if (!matchTitle) {
                    val title = match(readLine, bookNamePattern)
                    if (!title.isNullOrBlank()) {
                        matchTitle = true
                        book.book.title = title
                        continue
                    }
                }
                if (!matchAuthor) {
                    val author = match(readLine, bookAuthorPattern)
                    if (!author.isNullOrBlank()) {
                        matchAuthor = true
                        book.book.author = author
                        continue
                    }
                }

                if (!matchIntro) {
                    val intro = match(readLine, chapterNamePattern)
                    if (!intro.isNullOrBlank()) {
                        matchIntro = true
                    } else {
                        sb.append(readLine)
                        sb.append("<br/>")
                    }
                }

                if (matchTitle && matchAuthor && matchIntro) {
                    break
                }
            }
            book.book.intro = sb.toString()
        }

        if (book.book.title.isEmpty()) {
            book.book.title = "导入TXT"
        }

        return@withIo book
    }

    private val bookNamePattern = listOf(Pattern.compile("^.*《(.+)》.*$"))
    private val bookAuthorPattern = listOf(Pattern.compile("^作者：?(.+)$"))
    private val chapterNamePattern = listOf(
        //匹配 "第xx章 xxxx"
        "^ *[序第]{1} ?[0-9[一二三四五六七八九零十百千万]]+ ?[篇章节回]{1}.*$",
        //匹配 "xx章 xxxx"
        "^ *[0-9[一二三四五六七八九零十百千万]]+ ?[篇章节回]{1}.+$",
        //匹配 "123 xxxx"
        "^ +[0-9[一二三四五六七八九零十百千万]]+ .+$",
        "^ *序章 *$"
    ).map(Pattern::compile)

    /**
     * 匹配章节名。找到则返回章节名否则返回空字符串
     */
    private fun match(string: String?, pattern: List<Pattern>): String? {
        val title = string ?: return null
        if (title.isBlank()) {
            return null
        }
        pattern.forEach {
            val matcher = it.matcher(title)

            if (matcher.find()) {
                return if (matcher.groupCount() < 1) {
                    title.trim()
                } else {
                    matcher.group(1)?.trim() ?: title
                }
            }
        }
        return null
    }


    class TxtBook {
        var book: Book = Book()
        var charset: String = "utf-8"

        init {

            book.source = BOOK_SOURCE_FILE_IMPORT
//            book.title = "bookTitle"
            book.author = "导入书籍"
//                book.cover = bookCover
//            book.url = "bookUrl"
        }
    }
}