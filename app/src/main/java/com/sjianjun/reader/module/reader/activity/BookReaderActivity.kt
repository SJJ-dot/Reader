package com.sjianjun.reader.module.reader.activity

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ImmersionBar
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.module.main.fragment.ChapterListFragment
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import kotlinx.android.synthetic.main.activity_book_reader.*
import kotlinx.android.synthetic.main.reader_item_activity_chapter_content.view.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import java.util.*
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
            .statusBarDarkFont(true)
            .hideBar(BarHide.FLAG_HIDE_STATUS_BAR)
            .init()
        //先不显示
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.drawer_chapter_list,
                fragmentCreate<ChapterListFragment>(BOOK_URL, bookUrl)
            )
            .commitNowAllowingStateLoss()

        recycle_view.adapter = adapter
        initTime()
        initCenterClick()
        initScrollLoadChapter()
        initData()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        initData()
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

    private fun initTime() {
        viewLaunch {
            val format = simpleDateFormat("HH:mm")
            time.text = format.format(Date())

            val delay = simpleDateFormat("ss")
            delay((61 - delay.format(Date()).toInt()) * 1000L)

            while (true) {
                time.text = format.format(Date())
                delay(60000)
            }
        }
    }

    private fun initCenterClick() {
        recycle_view.centerClickListener = View.OnClickListener {
            if (recycle_view.touchable) {
                ImmersionBar.with(this).hideBar(BarHide.FLAG_SHOW_BAR).init()
                recycle_view.touchable = false
            } else {
                recycle_view.touchable = true
                ImmersionBar.with(this).hideBar(BarHide.FLAG_HIDE_STATUS_BAR).init()
            }
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

                val chapter = adapter.chapterList.getOrNull(firstPos) ?: return
                chapter_title.text = chapter.title

                if (preFirstPosition != firstPos) {
                    preFirstPosition = firstPos
                    loadRefresh(manager, firstPos)
                }
                if (preLastPos != lastPos) {
                    preLastPos = lastPos
                    loadRefresh(manager, lastPos)
                }

                saveReadRecord()
            }
        })
    }

    private val readingRecordJob = AtomicReference<Job>()
    private fun saveReadRecord(delay: Long = 2000) {
        readingRecordJob.get()?.cancel()
        viewLaunch {
            //延迟2s 保存
            delay(delay)
            val manager = recycle_view.layoutManager as LinearLayoutManager
            val view = manager.getChildAt(0) ?: return@viewLaunch
            val top = view.top
            val pos = manager.getPosition(view)
            val readingChapter = adapter.chapterList.getOrNull(pos)
            readingRecord.chapterUrl = readingChapter?.url ?: readingRecord.chapterUrl
            readingRecord.offest = top
            val isEnd = view.height + view.top - recycle_view.height < recycle_view.height / 6
            readingRecord.isEnd = isEnd
            DataManager.setReadingRecord(readingRecord)
        }.apply(readingRecordJob::lazySet)
    }

    private val initDataJob = AtomicReference<Job>()
    private fun initData() {
        initDataJob.get()?.cancel()
        viewLaunch {
            val book = DataManager.getBookByUrl(bookUrl).first()
            if (book == null) {
                finish()
                return@viewLaunch
            }
            this@BookReaderActivity.book = book

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
            DataManager.getChapterList(bookUrl)
                .onEach {
                    getChapterContent(it, readingRecord.chapterUrl)
                }.collectLatest {
                    if (adapter.chapterList.size != it.size) {
                        adapter.chapterList = it
                        adapter.notifyDataSetChanged()
                    }
                    if (first) {
                        first = false
                        val index = it.indexOfFirst { chapter ->
                            chapter.url == readingRecord.chapterUrl
                        }

                        if (index != -1) {
                            val manager = recycle_view.layoutManager as LinearLayoutManager
                            manager.scrollToPositionWithOffset(index, readingRecord.offest)
                        }
                    }

                }

        }.also(initDataJob::lazySet)
    }

    private fun loadRefresh(manager: LinearLayoutManager, position: Int) {
        val chapter = adapter.chapterList.getOrNull(position)
        viewLaunch {
            getChapterContent(adapter.chapterList, chapter?.url)
            if (manager.findFirstVisibleItemPosition()
                <= min(position + 1, adapter.chapterList.size)
                && manager.findLastVisibleItemPosition()
                >= max(position - 1, 0)
            ) {
                adapter.notifyDataSetChanged()
            }
        }
    }

    private val loadRecord = ConcurrentHashMap<String, Deferred<Chapter>>()
    /**
     * 加载 上一章 当前章 下一章
     */
    private suspend fun getChapterContent(chapterList: List<Chapter>?, chapterUrl: String?) {

        if (chapterList.isNullOrEmpty()) {
            return
        }

        val chapterIndex = if (chapterUrl.isNullOrBlank()) {
            0
        } else {
            chapterList.indexOfFirst { it.url == chapterUrl }
        }

        ((chapterIndex - 1)..(chapterIndex + 1)).mapNotNull {
            val chapter = chapterList.getOrNull(it)
            if (chapter != null) {
                if (chapter.isLoaded && chapter.content != null) {
                    null
                } else {
                    val loading = loadRecord[chapter.url]
                    if (loading == null) {
                        val load = async { DataManager.getChapterContent(chapter) }
                        loadRecord[chapter.url] = load
                        load
                    } else {
                        loading
                    }
                }
            } else {
                null
            }
        }.awaitAll().forEach {
            loadRecord.remove(it.url)
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
            holder.itemView.chapter_title.text = chapter.title
            if (chapter.content != null) {
                holder.itemView.chapter_content.text = chapter.content?.content.html()
                if (chapter.isLoaded) {
                    holder.itemView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                } else {
                    holder.itemView.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                }
            } else {
                holder.itemView.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                holder.itemView.chapter_content.text =
                    "拼命加载中…………………………………………………………………………………………………………………………"
            }
        }

        override fun getItemId(position: Int): Long {
            return chapterList[position].index.toLong()
        }
    }
}
