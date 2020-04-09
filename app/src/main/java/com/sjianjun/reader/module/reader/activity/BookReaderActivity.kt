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
import com.sjianjun.reader.utils.id
import kotlinx.android.synthetic.main.activity_book_reader.*
import kotlinx.android.synthetic.main.reader_item_activity_chapter_content.view.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import sjj.alog.Log
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

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
        viewLaunch {
            val book = DataManager.getBookByUrl(bookUrl).first()
            if (book == null) {
                finish()
                return@viewLaunch
            }
            this@BookReaderActivity.book = book
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
                Log.e("chapter change")
                adapter.chapterList = it
                adapter.notifyDataSetChanged()
                val index = adapter.chapterList.indexOfFirst { chapter ->
                        chapter.url == readingRecord.chapterUrl
                    }
                if (index != -1 && first) {
                    first = false
                    recycle_view.scrollToPosition(index)
                }
            }

        }
    }

    override fun onPause() {
        super.onPause()
        viewLaunch {
            val manager = recycle_view.layoutManager as LinearLayoutManager
            val pos = manager.findLastVisibleItemPosition()
            val readingChapter = adapter.chapterList.getOrNull(pos)
            readingRecord.chapterUrl = readingChapter?.url ?: readingRecord.chapterUrl
            DataManager.setReadingRecord(readingRecord)
        }
    }

    private val loadRecord = ConcurrentHashMap<String, Deferred<Chapter>>()
    private suspend fun getChapterContent(chapterList: List<Chapter>?, chapterUrl: String?) {

        if (chapterList.isNullOrEmpty()) {
            return
        }

        val chapterIndex = if (chapterUrl.isNullOrBlank()) {
            0
        } else {
            chapterList.indexOfFirst { it.url == chapterUrl }
        }

        (max(chapterIndex - 1, 0) until (chapterIndex + 1)).mapNotNull {
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
            Log.e("load ${it.index}")
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
            Log.e("onBindViewHolder pos:$position   ${chapter.content?.isNotEmpty() == true}")
            holder.itemView.chapter_title.text = chapter.title
            if (chapter.content?.isNotEmpty() == true) {
                holder.itemView.chapter_content.text = chapter.content.html()
                if (chapter.isLoaded) {
                    holder.itemView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                } else {
                    holder.itemView.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                }
                activity.viewLaunch {
                    activity.getChapterContent(chapterList, chapter.url)
                }
            } else {
                holder.itemView.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                holder.itemView.chapter_content.text = chapter.content.html()
                activity.viewLaunch {
                    activity.getChapterContent(chapterList, chapter.url)
                    if (holder.adapterPosition == position) {
                        holder.itemView.chapter_content.text = chapter.content.html()
                        if (chapter.isLoaded) {
                            holder.itemView.layoutParams.height =
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        } else {
                            holder.itemView.layoutParams.height =
                                ViewGroup.LayoutParams.MATCH_PARENT
                        }
                        holder.itemView.requestLayout()
                        Log.e("pos:$position")
                    }
                }

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
