package com.sjianjun.reader.module.reader.activity

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import com.sjianjun.coroutine.launchIo
import com.sjianjun.coroutine.withIo
import com.sjianjun.coroutine.withMain
import com.sjianjun.reader.App
import com.sjianjun.reader.BaseActivity
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
import com.sjianjun.reader.utils.show
import com.sjianjun.reader.utils.startActivity
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.utils.trimExt
import com.sjianjun.reader.view.click
import io.legado.app.lib.icu4j.CharsetDetector
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import sjj.alog.Log
import java.io.*
import java.nio.charset.Charset
import java.util.regex.Pattern
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

        book.title = binding.etName.text.toString().trimExt()
        book.author = binding.etAuthor.text.toString().trimExt()
        book.intro = binding.etIntro.text.toString().trimExt()
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
                setStatus("解析章节信息……")
                contentResolver.openInputStream(uri).use {
                    val reader = BufferedReader(InputStreamReader(it, book.charset))
                    binding.progressBar.max = it?.available() ?: 0
                    val chapterContent = StringBuilder()
                    var line: String? = reader.readLine()
                    var chapterName: String? = null
                    while (line != null) {
                        binding.progressBar.progress += line.length * 2
                        if (line.isBlank()) {
                            line = reader.readLine()
                            continue
                        }

                        line = line.trimExt()
                        val lastChapterName = chapterName
                        val matchName = match(line, chapterNamePattern, 0, book.tocRule)

                        if (matchName.isNullOrBlank()) {
                            if (line.isNotBlank()) {
                                chapterContent.append(line)
                                chapterContent.append("\n")
                            }
                        } else {
                            chapterName = matchName
                        }
                        //章节内容不为空 并且匹配到章节名或者章节字数超过限制
                        if (!matchName.isNullOrBlank() || chapterContent.length > CHAPTER_CONTENT_MAX_LENGTH) {
                            if (!lastChapterName.isNullOrBlank() || chapterContent.isNotBlank()) {
                                val chapterContentStr = if (chapterContent.isBlank()) lastChapterName else chapterContent.toString()
                                createChapter(book, chapterList, lastChapterName, chapterContentStr)
                            }

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
        chapterContent: String?
    ) {
        val chapter = Chapter()
        chapter.url = "${book.book.url}_${chapterList.size}"
        chapter.bookId = book.book.id
        chapter.title = if (chapterName.isNullOrBlank()) "第${chapterList.size + 1}章" else chapterName.trimExt()
        chapter.index = chapterList.size
        chapterList.add(chapter)

        contentDao.insert(
            ChapterContent(
                chapter.bookId,
                chapter.index,
                chapterContent ?: chapter.title!!
            )
        )
    }

    private suspend fun readBookInfo(uri: Uri): TxtBook = withIo {
        setStatus("解析书籍信息")
        var fileName = uri.lastPathSegment ?: ""
        val index = fileName.indexOf(".")
        if (index > 0) {
            fileName = fileName.substring(0, index)
        }
        val book = TxtBook()
        book.book.bookSourceId = BookSource.fileImportBookSource.id
        book.book.url = uri.toString()
        book.book.title = fileName
        book.book.author = "佚名"

        var matchTitle = false
        var matchAuthor = false
        var matchIntro = false

        for (pattern in nameAuthorPatterns) {
            pattern.matcher(fileName).takeIf { it.find() }?.run {
                val title = group(2)!!.replace(nameRegex, "").trimExt()
                val group1 = group(1) ?: ""
                val group3 = group(3) ?: ""
                val author = (group1 + group3).replace(authorRegex, "").trimExt()
                if (title.isNotBlank()) {
                    book.book.title = title
                    matchTitle = true
                }
                if (author.isNotBlank()) {
                    book.book.author = author
                    matchAuthor = true
                }
                Log.i("通过文件名匹配到书名和作者，书名：${book.book.title}，作者：${book.book.author}")
                break
            }
        }
        if (!matchTitle) {
            var title = match(fileName, bookNamePattern, 1)
            if (title.isNullOrBlank()) {
                title = fileName.replace(nameRegex, "").trimExt()
            }
            if (title.isNotBlank()) {
                book.book.title = title
                matchTitle = true
            }
        }
        if (!matchAuthor) {
            var author = match(fileName, bookAuthorPattern, 1)
            if (author.isNullOrBlank()) {
                val title = fileName.replace(nameRegex, "").trimExt()
                author = fileName.replace(title, "")
                    .replace(authorRegex, "").trimExt()
                    .takeIf { it.length != fileName.length } ?: "佚名"
            }
            if (author.isNotBlank()) {
                book.book.author = author
                matchAuthor = true
            }
        }

        contentResolver.openInputStream(uri).use {
            var buffer = ByteArray(500 * 1024)
            val len = it!!.read(buffer)
            buffer = buffer.copyOf(len)
            val name = CharsetDetector().setText(buffer).detect()?.name ?: "UTF_8"
            book.charset = Charset.forName(name)
            book.tocRule = getTocRule(String(buffer, book.charset))
        }
        contentResolver.openInputStream(uri).use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream, book.charset))

            val sb = StringBuilder()
            var times = 0
            while (++times < 30) {
                val readLine = reader.readLine()

                if (!matchTitle) {
                    val title = match(readLine, bookNamePattern, 1)
                    if (!title.isNullOrBlank()) {
                        matchTitle = true
                        book.book.title = title
                        continue
                    }
                }
                if (!matchAuthor) {
                    val author = match(readLine, bookAuthorPattern, 1)
                    if (!author.isNullOrBlank()) {
                        matchAuthor = true
                        book.book.author = author
                        continue
                    }
                }

                if (!matchIntro) {
                    val intro = match(readLine, chapterNamePattern, 0, book.tocRule)
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
    private fun match(content: String?, pattern: List<Pattern>, group: Int, pattern0: Pattern? = null): String? {
        val content = content ?: return null
        if (content.isBlank()) {
            return null
        }
        if (pattern0 != null) {
            val matcher = pattern0.matcher("\n" + content + "\n")
            if (matcher.find()) {
                return matcher.group().trimExt()
            }
            return null
        }
        val contentTrim = content.trimExt()
        pattern.forEach {
            val matcher = it.matcher(contentTrim)

            if (matcher.find()) {
                return try {
                    matcher.group(group)?.trimExt() ?: contentTrim
                } catch (e: Exception) {
                    contentTrim
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
        var toc: List<String>? = null
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
            var num = 0
            val tocTemp = mutableListOf<String>()
            while (matcher.find()) {
                num++
                tocTemp.add(matcher.group().trimExt())
            }
            if (num >= maxNum) {
                maxNum = num
                tocPattern = pattern
                toc = tocTemp
            }
        }
        Log.i("匹配到目录规则：$tocPattern，章节数：$maxNum，目录示例：${toc?.take(15)}")
        return tocPattern
    }


    class TxtBook {
        var book: Book = Book()
        var charset = Charsets.UTF_8
        var tocRule: Pattern? = null
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