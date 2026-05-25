package com.sjianjun.reader.module.reader.activity

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import com.sjianjun.charset.CharsetDetector
import com.sjianjun.coroutine.launchIo
import com.sjianjun.coroutine.withIo
import com.sjianjun.coroutine.withMain
import com.sjianjun.reader.App
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ChapterContent
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.databinding.ReaderActivityFileImportBinding
import com.sjianjun.reader.module.main.MainActivity
import com.sjianjun.reader.repository.BookUseCase
import com.sjianjun.reader.repository.DbFactory
import com.sjianjun.reader.repository.DbFactory.db
import com.sjianjun.reader.utils.format
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gone
import com.sjianjun.reader.utils.gson
import com.sjianjun.reader.utils.hide
import com.sjianjun.reader.utils.show
import com.sjianjun.reader.utils.startActivity
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.click
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import sjj.alog.Log
import java.io.*
import java.nio.charset.Charset
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import kotlin.coroutines.resume
import kotlin.math.max

/*
 * Created by shen jian jun on 2020-07-20
 */
class FileImportActivity : BaseActivity() {
    private val binding by lazy { ReaderActivityFileImportBinding.inflate(layoutInflater) }
    private val bookDao get() = DbFactory.db.bookDao()
    private val readingRecordDao get() = DbFactory.db.readingRecordDao()
    private val chapterDao get() = db.chapterDao()
    private val contentDao get() = db.chapterContentDao()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.tilName.gone()
        binding.tilAuthor.gone()
        binding.tilIntro.gone()
        binding.mbCancel.gone()
        binding.mbConfirm.gone()
        binding.progressBar.show()
        binding.mbCancel.click {
            startActivity<MainActivity>()
            finish()
        }
        val intent: Intent = intent
        val uri: Uri? = intent.data
        readTxt(uri)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val intent: Intent = intent
        val uri: Uri? = intent.data
        readTxt(uri)
    }

    //状态刷新至少间隔1秒。给用户看看
    private var lastStatusTime = 0L
    private suspend fun setStatus(status: String) {
        delay(max(1000 - (System.currentTimeMillis() - lastStatusTime), 0))
        lastStatusTime = System.currentTimeMillis()
        withMain { binding.status.text = status }
    }

    private suspend fun waitUserConfirm(book: Book) = withMain {
        binding.tilName.show()
        binding.tilAuthor.show()
        binding.tilIntro.show()
        binding.mbCancel.show()
        binding.mbConfirm.show()
        binding.etName.setText(book.title)
        binding.etAuthor.setText(book.author)
        binding.etIntro.setText(book.intro.format(true))
        setStatus("请确认书籍信息")
        suspendCancellableCoroutine<Unit> { cont ->
            binding.mbConfirm.setOnClickListener {
                binding.tilName.gone()
                binding.tilAuthor.gone()
                binding.tilIntro.gone()
                binding.mbCancel.gone()
                binding.mbConfirm.gone()
                cont.resume(Unit)
            }
        }

        book.title = binding.etName.text.toString().trim()
        book.author = binding.etAuthor.text.toString().trim()
        book.intro = binding.etIntro.text.toString().trim()
    }

    private fun readTxt(uri: Uri?) {
        Log.e("uri:$uri")
        launchIo {

            try {
                Log.e("readBookInfo")
                val book = readBookInfo(uri!!)
                waitUserConfirm(book.book)
                BookUseCase.cleanDirtyData()
                bookDao.insertBook(listOf(book.book))
                val readingRecord = readingRecordDao.getReadingRecordSync(book.book.title)
                Log.i("保存阅读记录：$readingRecord $book")
                if (readingRecord == null) {
                    readingRecordDao.insertReadingRecord(ReadingRecord(book.book.title, book.book.id))
                }
                //删除旧章节信息
                chapterDao.deleteChapterByBookId(book.book.id)
                //保存章节信息
                val chapterList = mutableListOf<Chapter>()
                //单章最大字数
                val CHAPTER_CONTENT_MAX_LENGTH = 20000
                Log.e("读取章节内容")
                setStatus("解析章节信息……")
                contentResolver.openInputStream(uri).use {
                    val reader = BufferedReader(InputStreamReader(it, book.charset))
                    binding.progressBar.max = it?.available() ?: 0
                    val chapterContent = StringBuilder()
                    var line: String? = reader.readLine()
                    var chapterName: String? = book.book.title
                    while (line != null) {
                        binding.progressBar.progress += line.length * 2
                        if (line.isBlank()) {
                            line = reader.readLine()
                            continue
                        }

                        val lastChapterName = chapterName
                        val matchName = match(line, chapterNamePattern, book.tocRule)

                        if (matchName.isNullOrBlank()) {
                            chapterContent.append(line)
                            chapterContent.append("<br/>")
                        } else {
                            chapterName = matchName
                        }
                        //章节内容不为空 并且匹配到章节名或者章节字数超过限制
                        if (chapterContent.isNotEmpty() && (!matchName.isNullOrBlank() || chapterContent.length > CHAPTER_CONTENT_MAX_LENGTH)) {
                            createChapter(book, chapterList, lastChapterName, chapterContent.toString())
                            chapterContent.clear()
                        }
                        line = reader.readLine()
                    }
                    if (chapterContent.isNotBlank()) {
                        createChapter(book, chapterList, chapterName, chapterContent.toString())
                    }
                }
                Log.e("章节读取完成")
                setStatus("章节解析完成，更新书籍信息……")
                book.book.chapterList = chapterList
                chapterDao.insertChapter(chapterList)
                chapterDao.markChaptersAsLoadedByBookId(book.book.id)
                setStatus("小说导入成功")
                delay(2000)
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
        chapter.bookId = book.book.id
        chapter.title = chapterName?.trim() ?: ""
        chapter.index = chapterList.size
        chapterList.add(chapter)

        contentDao.insert(
            ChapterContent(
                chapter.bookId,
                chapter.index,
                chapterContent
            )
        )
    }

    private suspend fun readBookInfo(uri: Uri): TxtBook = withIo {
        setStatus("解析书籍信息")
        val book = TxtBook()
        book.charset = contentResolver.openInputStream(uri).use { Charset.forName(CharsetDetector.detectCharset(it)) }
        book.book.url = uri.toString()
        var matchTitle = false
        var matchAuthor = false
        var matchIntro = false

        for (pattern in nameAuthorPatterns) {
            pattern.matcher(book.book.title).takeIf { it.find() }?.run {
                book.book.title = group(2)!!.replace(nameRegex, "").trim()
                val group1 = group(1) ?: ""
                val group3 = group(3) ?: ""
                book.book.author = (group1 + group3).replace(authorRegex, "").trim()
                matchTitle = true
                matchAuthor = true
                break
            }
        }
        if (!matchTitle) {
            var fileName = uri.lastPathSegment ?: ""
            val index = fileName.indexOf(".")
            if (index > 0) {
                fileName = fileName.substring(0, index)
            }
            book.book.title = fileName.replace(nameRegex, "").trim()
            book.book.author = fileName.replace(book.book.title, "")
                .replace(authorRegex, "").trim()
                .takeIf { it.length != fileName.length } ?: "佚名"
        }

        contentResolver.openInputStream(uri).use {
            val buffer = ByteArray(500 * 1024)
            it!!.read(buffer)
            val content = String(buffer, book.charset)
            book.tocRule = getTocRule(content)
        }
        contentResolver.openInputStream(uri).use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream, book.charset))

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
                    val intro = match(readLine, chapterNamePattern, book.tocRule)
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

    val nameRegex = Regex("\\s+作\\s*者.*|\\s+\\S+\\s+著")
    val authorRegex = Regex("^\\s*作\\s*者[:：\\s]+|\\s+著")
    private val nameAuthorPatterns = arrayOf(
        Pattern.compile("(.*?)《([^《》]+)》.*?作者：(.*)"),
        Pattern.compile("(.*?)《([^《》]+)》(.*)"),
        Pattern.compile("(^)(.+) 作者：(.+)$"),
        Pattern.compile("(^)(.+) by (.+)$")
    )

    private val bookNamePattern = listOf(Pattern.compile("^.*《(.+)》.*$"))
    private val bookAuthorPattern = listOf(Pattern.compile("^作者：?(.+)$"), Pattern.compile("^(.+) 著$"))
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
    private fun match(content: String?, pattern: List<Pattern>, pattern0: Pattern? = null): String? {
        val content = content ?: return null
        if (content.isBlank()) {
            return null
        }
        if (pattern0 != null) {
            val matcher = pattern0.matcher(content)
            if (matcher.find()) {
                return matcher.group().trim()
            }
            return null
        }
        val contentTrim = content.trim()
        pattern.forEach {
            val matcher = it.matcher(contentTrim)

            if (matcher.find()) {
                return if (matcher.groupCount() < 1) {
                    contentTrim
                } else {
                    matcher.group().trim()
                }
            }
        }
        return null
    }


    /**
     * 获取合适的目录规则
     */
    private fun getTocRule(content: String): Pattern? {
        val json = App.app.assets.open("defaultData${File.separator}txtTocRule.json").readBytes().toString(Charsets.UTF_8)
        val rules = gson.fromJson<List<TocRule>>(json) ?: return null

        var maxNum = 1
        var tocPattern: Pattern? = null
        for (i in rules.size - 1 downTo 0) {
            val item = rules[i]
            if (!item.enable) {
                continue
            }
            val pattern = try {
                item.rule.toPattern(Pattern.MULTILINE)
            } catch (e: Exception) {
                continue
            }
            val matcher = pattern.matcher(content)
            var start = 0
            var num = 0
            while (matcher.find()) {
                if (start == 0 || matcher.start() - start > 1000) {
                    num++
                    start = matcher.end()
                }
            }
            if (num >= maxNum) {
                maxNum = num
                tocPattern = pattern
            }
        }
        return tocPattern
    }


    class TxtBook {
        var book: Book = Book()
        var charset = Charsets.UTF_8
        var tocRule: Pattern? = null

        init {
            book.bookSourceId = BookSource.fileImportBookSource.id
            book.author = "佚名"
        }
    }

    class TocRule {
        var rule: String = ""
        var enable: Boolean = false
    }

    companion object {
        fun start(context: Activity, uri: Uri?) {
            val intent = Intent(context, FileImportActivity::class.java)
            intent.data = uri
            context.startActivity(intent)
        }
    }

}