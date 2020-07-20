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

    private fun readTxt(uri: Uri?) {
        Log.e("uri:$uri")
        launchIo {

            try {
                Log.e("readBookInfo")
                withMain { status.text = "读取书籍信息" }
                val book = readBookInfo(uri!!)
                Log.e("insertBookAndSaveReadingRecord")
                withMain { status.text = "保存书籍信息" }
                DataManager.insertBookAndSaveReadingRecord(listOf(book.book))
                //保存章节信息
                val chapterList = mutableListOf<Chapter>()
                //单章最大字数
                val CHAPTER_CONTENT_MAX_LENGTH = 10000
                Log.e("读取章节内容")
                withMain { status.text = "解析并保存单章内容" }
                contentResolver.openInputStream(uri).use {
                    val reader = BufferedReader(InputStreamReader(it, book.charset))
                    progress_bar.max = it?.available() ?: 0
                    val chapterContent = StringBuilder()
                    var line: String? = reader.readLine()
                    var chapterName: String? = book.book.title
                    while (line != null) {
                        //这个进度不准确。
                        progress_bar.progress = progress_bar.progress + line.length * 2
                        if (line.isNullOrBlank()) {
                            line = reader.readLine()
                            continue
                        }

                        val lastChapterName = chapterName
                        val matchName = match(line, chapterNamePattern)

                        if (matchName.isNullOrBlank()) {
                            chapterContent.append(line)
                            chapterContent.append("<br/>")
                        } else {
                            chapterName = matchName
                        }
                        //章节内容不为空 并且匹配到章节名或者章节字数超过限制
                        if (chapterContent.isNotEmpty() && (!matchName.isNullOrBlank() || chapterContent.length > CHAPTER_CONTENT_MAX_LENGTH)) {

                            createChapter(
                                book,
                                chapterList,
                                lastChapterName,
                                chapterContent.toString()
                            )

                            chapterContent.clear()
                        }
                        line = reader.readLine()
                    }
                    if (chapterContent.isEmpty()) {
                        createChapter(book, chapterList, chapterName, chapterContent.toString())
                    }
                }
                Log.e("章节读取完成")
                withMain { status.text = "章节解析完成，更新书籍信息……" }
                book.book.chapterList = chapterList
                DataManager.updateBookDetails(book.book)
                withMain {
                    //导入小说创建成功
                    //
                    Log.e("小说导入成功")
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
            book.book.title
        else
            chapterName

        chapter.index = chapterList.size
        chapterList.add(chapter)
        chapter.title = "第${chapterList.size}章 ${chapter.title}"

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
            txtBook.book.title = fileName ?: "未知书名"
            txtBook.book.url = uri.toString()
            txtBook
        }

        withMain { status.text = "解析书籍信息" }

        contentResolver.openInputStream(uri).use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream, book.charset))
            var matchTitle = false
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

        return@withIo book
    }

    private val bookNamePattern = listOf(Pattern.compile("^.*《(.+)》.*$"))
    private val bookAuthorPattern = listOf(Pattern.compile("^作者：?(.+)$"))
    private val chapterNamePattern = listOf(
        "^第[0-9[一二三四五六七八九零十百千万]]+[章节回](.+$)",
        "^第[0-9[一二三四五六七八九零十百千万]]+ (.+$)",
        "^[0-9[一二三四五六七八九零十百千万]]+[章节回](.+$)",
        "^[0-9[一二三四五六七八九零十百千万]]+$",
        "^序[0-9[一二三四五六七八九零十百千万]]+[章节回](.+$)",
        "^序章$"
    ).map(Pattern::compile)

    /**
     * 匹配章节名。找到则返回章节名否则返回空字符串
     */
    private fun match(string: String?, pattern: List<Pattern>): String? {
        val title = string?.trim() ?: return null
        if (title.isBlank()) {
            return null
        }
        pattern.forEach {
            val matcher = it.matcher(title)

            if (matcher.find()) {
                return if (matcher.groupCount() < 1) {
                    title
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