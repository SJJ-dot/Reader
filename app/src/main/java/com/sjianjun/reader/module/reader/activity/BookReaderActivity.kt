package com.sjianjun.reader.module.reader.activity

import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ImmersionBar
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.BOOK_URL
import com.sjianjun.reader.utils.CHAPTER_URL
import kotlinx.android.synthetic.main.activity_book_reader.*
import kotlinx.android.synthetic.main.activity_book_reader.chapter_title
import kotlinx.android.synthetic.main.reader_item_activity_chapter_content.view.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import sjj.alog.Log
import java.util.concurrent.ConcurrentHashMap

class BookReaderActivity : BaseActivity() {
    private val bookUrl by lazy { intent.getStringExtra(BOOK_URL)!! }
    private val chapterUrl by lazy { intent.getStringExtra(CHAPTER_URL) }
    private lateinit var book: Book
    private lateinit var readingRecord: ReadingRecord
    private val adapter by lazy { ChapterListAdapter(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_reader)
        ImmersionBar.with(this).hideBar(BarHide.FLAG_HIDE_STATUS_BAR).init()
        //先不显示
        drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        recycle_view.adapter = adapter

        initScrollLoadChapter()
        initData()
    }


    override fun onPause() {
        super.onPause()
        saveReadRecord()
    }

    private fun saveReadRecord() = viewLaunch {
        val manager = recycle_view.layoutManager as LinearLayoutManager
        val pos = manager.findLastVisibleItemPosition()
        val readingChapter = adapter.chapterList.getOrNull(pos)
        readingRecord.chapterUrl = readingChapter?.url ?: readingRecord.chapterUrl
        DataManager.setReadingRecord(readingRecord)
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
                    viewLaunch {
                        val isEmpty = chapter.content.isNullOrEmpty()
                        getChapterContent(adapter.chapterList, chapter.url)
                        if (isEmpty && manager.findFirstVisibleItemPosition() == firstPos) {
                            adapter.notifyDataSetChanged()
                        }
                    }
                }
                if (preLastPos != lastPos) {
                    preLastPos = lastPos
                    val lastChapter = adapter.chapterList.getOrNull(lastPos)
                    viewLaunch {
                        val isEmpty = lastChapter?.content.isNullOrEmpty()
                        getChapterContent(adapter.chapterList, lastChapter?.url)
                        if (isEmpty && manager.findLastVisibleItemPosition() == lastPos) {
                            adapter.notifyDataSetChanged()
                        }
                    }
                }

            }
        })
    }

    private fun initData() {
        viewLaunch {
            val book = DataManager.getBookByUrl(bookUrl).first()
            if (book == null) {
                finish()
                return@viewLaunch
            }
            this@BookReaderActivity.book = book
            //书籍标题
            book_title.text = book.title

            readingRecord =
                DataManager.getReadingRecord(book).first() ?: ReadingRecord(book.title, book.author)

            if (readingRecord.bookUrl == bookUrl) {
                readingRecord.chapterUrl = chapterUrl ?: readingRecord.chapterUrl
            } else {
                readingRecord.bookUrl = bookUrl
                readingRecord.chapterUrl = chapterUrl ?: ""
            }

            var first = true
            DataManager.getChapterList(bookUrl).onEach {
                getChapterContent(it, readingRecord.chapterUrl)
            }.collectLatest {
                if (adapter.chapterList.size != it.size) {
                    adapter.chapterList = it
                    adapter.notifyDataSetChanged()

                    if (first) {
                        first = false
                        val index = adapter.chapterList.indexOfFirst { chapter ->
                            chapter.url == readingRecord.chapterUrl
                        }
                        if (index != -1) {
                            recycle_view.scrollToPosition(index)
                        }
                    }

                }
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
                if (chapter.isLoaded && chapter.content?.isNotEmpty() == true) {
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
            Log.e("load index:${it.index} ${it.title}")
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
            if (chapter.content?.isNotEmpty() == true) {
                holder.itemView.chapter_content.text = chapter.content.html()
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

        private fun String?.html(): Spanned {
            return Html.fromHtml(this ?: "", Html.FROM_HTML_MODE_COMPACT)
        }
    }
}
