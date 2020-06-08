package com.sjianjun.reader.module.reader.activity

import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gyf.immersionbar.ImmersionBar
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.module.main.ChapterListFragment
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import kotlinx.android.synthetic.main.activity_book_reader.*
import kotlinx.android.synthetic.main.reader_item_activity_chapter_content.view.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

class BookReaderActivity : BaseActivity() {
    private val bookUrl get() = intent.getStringExtra(BOOK_URL)!!
    private val chapterUrl get() = intent.getStringExtra(CHAPTER_URL)
    private lateinit var book: Book
    private lateinit var readingRecord: ReadingRecord
    private val adapter by lazy { ChapterListAdapter(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_reader)
        ImmersionBar.with(this)
            .statusBarColor(R.color.day_night_reader_content_background)
            .statusBarDarkFont(globalConfig.appDayNightMode == AppCompatDelegate.MODE_NIGHT_NO)
//            .hideBar(BarHide.FLAG_HIDE_STATUS_BAR)
            .init()

        recycle_view.adapter = adapter
        initScrollLoadChapter()
        initData()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (this::readingRecord.isInitialized && readingRecord.bookUrl == bookUrl) {
            val targetChapter = adapter.chapterList.indexOfFirst { it.url == chapterUrl }
            if (targetChapter != -1) {
                val manager = recycle_view.layoutManager as LinearLayoutManager
                manager.scrollToPositionWithOffset(targetChapter, 0)
            }
        } else {
            initData()
        }
    }

    override fun onPause() {
        super.onPause()
        saveReadRecord(0)
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.END)) {
            drawer_layout.closeDrawer(GravityCompat.END)
        } else {
            super.onBackPressed()
        }
    }

    private fun initScrollLoadChapter() {
        recycle_view.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var preFirstPosition = -1
            private var preLastPos = -1
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val manager = recyclerView.layoutManager as LinearLayoutManager
                val firstPos = manager.findFirstVisibleItemPosition()
                val lastPos = manager.findLastVisibleItemPosition()

                val chapterList = adapter.chapterList
                val chapter = chapterList.getOrNull(firstPos) ?: return
                chapter_title.text = chapter.title

                if (preFirstPosition != firstPos || preLastPos != lastPos) {
                    preFirstPosition = firstPos
                    preLastPos = lastPos
                    launch {
                        val intRange = (max(firstPos - 1, 0))..(min(lastPos + 1, chapterList.size))
                        preLoadRefresh(chapterList, intRange)
                        val curFirstPos = manager.findFirstVisibleItemPosition()
                        val curLastPos = manager.findLastVisibleItemPosition()
                        if (curFirstPos <= lastPos && curLastPos >= firstPos) {
                            delay(1)
                            adapter.notifyDataSetChanged()
                        }
                    }
                }
                saveReadRecord()
            }
        })
    }

    private val readingRecordJob = AtomicReference<Job>()
    private fun saveReadRecord(delay: Long = 2000) {
        readingRecordJob.get()?.cancel()
        launch {
            //延迟2s 保存
            delay(delay)
            val manager = recycle_view.layoutManager as LinearLayoutManager
            var view = manager.getChildAt(0) ?: return@launch
            var isEnd = view.height + view.top - recycle_view.height < recycle_view.height / 6

            if (isEnd && manager.findLastVisibleItemPosition() == adapter.chapterList.size - 1) {
                view = manager.getChildAt(manager.childCount - 1) ?: view
                isEnd = view.height + view.top - recycle_view.height < recycle_view.height / 6
            }

            val top = view.top
            val pos = manager.getPosition(view)
            val readingChapter = adapter.chapterList.getOrNull(pos)
            readingRecord.chapterUrl = readingChapter?.url ?: readingRecord.chapterUrl
            readingRecord.offest = top

            readingRecord.isEnd = isEnd
            DataManager.setReadingRecord(readingRecord)
        }.apply(readingRecordJob::lazySet)
    }

    private val initDataJob = AtomicReference<Job>()
    private fun initData() {
        initDataJob.get()?.cancel()
        launch {
            val book = DataManager.getBookByUrl(bookUrl).first()
            if (book == null) {
                finish()
                return@launch
            }
            this@BookReaderActivity.book = book

            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.drawer_chapter_list,
                    fragmentCreate<ChapterListFragment>(
                        BOOK_TITLE to book.title,
                        BOOK_AUTHOR to book.author
                    )
                )
                .commitNowAllowingStateLoss()

            readingRecord = DataManager.getReadingRecord(book).first()
                ?: ReadingRecord(book.title, book.author)

            if (readingRecord.bookUrl == bookUrl) {
                if (!chapterUrl.isNullOrBlank()) {
                    readingRecord.chapterUrl = chapterUrl
                    readingRecord.offest = 0
                }
            } else {
                readingRecord.bookUrl = bookUrl
                readingRecord.chapterUrl = chapterUrl ?: ""
                readingRecord.offest = 0
            }

            var first = true
            DataManager.getChapterList(bookUrl).collectLatest {

                if (first) {
                    first = false

                    var index = it.indexOfFirst { chapter ->
                        chapter.url == readingRecord.chapterUrl
                    }
                    if (index > -1) {
                        if (readingRecord.isEnd && index < it.size - 1) {
                            readingRecord.offest = 0
                            index++
                        }
                        if (!it[index].isLoaded) {
                            readingRecord.offest = 0
                        }
                    }
                    //只加载本地的数据
                    val intRange = max(index - 1, 0)..min(index + 1, it.size - 1)
                    preLoadRefresh(it, intRange, true)
                    if (adapter.chapterList.size != it.size) {
                        loadRecord.clear()
                        adapter.chapterList = it
                        adapter.notifyDataSetChanged()
                    }


                    if (index != -1) {
                        val manager = recycle_view.layoutManager as LinearLayoutManager
                        manager.scrollToPositionWithOffset(index, readingRecord.offest)
                    }
                } else {
                    if (adapter.chapterList.size != it.size) {
                        adapter.chapterList = it
                        adapter.notifyDataSetChanged()
                    }
                }
            }

        }.also(initDataJob::lazySet)
    }

    private suspend fun preLoadRefresh(
        chapterList: List<Chapter>,
        posRange: IntRange,
        onlyLocal: Boolean = false
    ) = withIo {
        val loadList = posRange.mapNotNull { chapterList.getOrNull(it) }
        loadList.map {
            async { getChapterContent(it, onlyLocal) }
        }.awaitAll()
    }

    private val loadRecord = ConcurrentHashMap<String, Deferred<Chapter>>()

    /**
     * 加载 上一章 当前章 下一章
     */
    private suspend fun getChapterContent(
        chapter: Chapter?,
        onlyLocal: Boolean
    ) {
        withIo {
            chapter ?: return@withIo
            if (chapter.isLoaded && chapter.content != null) {
                return@withIo
            }
            var loading = loadRecord[chapter.url]
            if (loading == null) {
                loading = async {
                    val chapterContent = DataManager.getChapterContent(chapter, onlyLocal)
                    chapterContent
                }
                loadRecord[chapter.url] = loading
            }

            loading.await()
            loadRecord.remove(chapter.url)
        }
    }

    class ChapterListAdapter(val activity: BookReaderActivity) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        init {
            setHasStableIds(true)
        }

        var chapterList = emptyList<Chapter>()
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return object : RecyclerView.ViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.reader_item_activity_chapter_content,
                    parent,
                    false
                )
            ) {}
        }

        override fun getItemCount(): Int = chapterList.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val chapter = chapterList[position]
            holder.itemView.apply {
                isClickable = false
                chapter_title.text = chapter.title
                chapter_title.isClickable = false
                if (chapter.content != null) {
                    val content = SpannableStringBuilder("${chapter.content?.content}".html())
                    Regex("\\A\\s*").find(content)?.apply {
                        content.replace(range.first,range.last+1,"")
                    }
                    content.insert(0,"\u3000\u3000")
                    var result = Regex("\n+\\s*").find(content)
                    while (result != null) {
                        content.replace(result.range.first,result.range.last+1,"\n\u3000\u3000")
                        result = result.next()
                    }
                    chapter_content.text = content
                    if (chapter.isLoaded) {
                        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        chapter_title.setOnClickListener {
                            activity.launch {
                                showSnackbar(it, "正在加载……")
                                DataManager.getChapterContent(chapter, false, force = true)
                                showSnackbar(it, "加载完成")
                                if (holder.adapterPosition == position) {
                                    delay(1)
                                    notifyItemChanged(position)
                                }
                            }
                        }
                    } else {
                        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                        setOnClickListener {
                            chapter_content.text =
                                "拼命加载中…………………………………………………………………………………………………………………………"
                            activity.launch {
                                val intRange =
                                    max(position - 1, 0)..min(position + 1, chapterList.size - 1)
                                activity.preLoadRefresh(chapterList, intRange)
                                if (holder.adapterPosition == position) {
                                    delay(1)
                                    notifyDataSetChanged()
                                }
                            }
                        }
                    }
                } else {
                    layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    chapter_content.text =
                        "拼命加载中…………………………………………………………………………………………………………………………"
                }
            }

        }

        override fun getItemId(position: Int): Long {
            return chapterList[position].index.toLong()
        }
    }
}
