package com.sjianjun.reader.module.reader.activity

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
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
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import kotlinx.android.synthetic.main.activity_book_reader.*
import kotlinx.android.synthetic.main.reader_item_activity_chapter_content.view.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
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
            .statusBarDarkFont(globalConfig.appDayNightMode == AppCompatDelegate.MODE_NIGHT_NO)
            .hideBar(BarHide.FLAG_HIDE_STATUS_BAR)
            .init()

        recycle_view.adapter = adapter
        initTime()
        initCenterClick()
        initScrollLoadChapter()
        initData()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (readingRecord.bookUrl == bookUrl) {
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

                if (preFirstPosition != firstPos || preLastPos != lastPos) {
                    preFirstPosition = firstPos
                    preLastPos = lastPos
                    preLoadRefresh(
                        manager,
                        (max(firstPos - 1, 0))..(min(lastPos + 1, adapter.chapterList.size))
                    )
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
            var view = manager.getChildAt(0) ?: return@viewLaunch
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
        viewLaunch {
            val book = DataManager.getBookByUrl(bookUrl).first()
            if (book == null) {
                finish()
                return@viewLaunch
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
                    getChapterContent(it, readingRecord.chapterUrl, true)
                    if (adapter.chapterList.size != it.size) {
                        loadRecord.clear()
                        adapter.chapterList = it
                        adapter.notifyDataSetChanged()
                    }

                    val index = it.indexOfFirst { chapter ->
                        chapter.url == readingRecord.chapterUrl
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

    private fun preLoadRefresh(
        manager: LinearLayoutManager,
        posRange: IntRange,
        async: Boolean = false
    ) {
        viewLaunch {
            withIo {
                val chapterList = adapter.chapterList
                val loadList = posRange.mapNotNull { chapterList.getOrNull(it) }
                loadList.map {
                    async { getChapterContent(chapterList, it.url, async) }
                }
            }?.joinAll()

            val firstPos = manager.findFirstVisibleItemPosition()
            val lastPos = manager.findLastVisibleItemPosition()
            if (firstPos <= posRange.last && lastPos >= posRange.first) {
                delay(1)
                adapter.notifyDataSetChanged()
            }
        }
    }

    private val loadRecord = ConcurrentHashMap<String, Deferred<Chapter>>()
    /**
     * 加载 上一章 当前章 下一章
     */
    private suspend fun getChapterContent(
        chapterList: List<Chapter>?,
        chapterUrl: String?,
        async: Boolean = false
    ) {
        withIo {
            val chapter = chapterList?.find { it.url == chapterUrl } ?: return@withIo
            if (chapter.isLoaded && chapter.content != null) {
                return@withIo
            }
            var loading = loadRecord[chapter.url]
            if (loading == null) {
                loading = async {
                    DataManager.getChapterContent(chapter, async)
                }
                loadRecord[chapter.url] = loading
            }
            loading.join()
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
