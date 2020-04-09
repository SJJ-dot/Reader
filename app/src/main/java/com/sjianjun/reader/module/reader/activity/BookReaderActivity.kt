package com.sjianjun.reader.module.reader.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ImmersionBar
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.BOOK_ID
import com.sjianjun.reader.utils.CHAPTER_ID
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
    private val bookId by lazy { intent.getStringExtra(BOOK_ID)!!.toInt() }
    private val chapterId by lazy { intent.getStringExtra(CHAPTER_ID)?.toInt() }
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
            val book = DataManager.getBookById(bookId).first()
            if (book == null) {
                finish()
                return@viewLaunch
            }
            this@BookReaderActivity.book = book
            readingRecord =
                DataManager.getReadingRecord(book).first() ?: ReadingRecord(book.title, book.author)

            if (readingRecord.readingBookId == bookId) {
                readingRecord.readingBookChapterId = chapterId ?: readingRecord.readingBookChapterId
            } else {
                readingRecord.readingBookId = bookId
                readingRecord.readingBookChapterId = chapterId ?: 0
            }


            DataManager.getChapterList(bookId).onEach {
                getChapterContent(it, readingRecord.readingBookChapterId)
            }.collectLatest {
                adapter.chapterList = it
                adapter.notifyDataSetChanged()
                val chapterId = readingRecord.readingBookChapterId
                val index = adapter.chapterList.indexOfFirst { chapter -> chapter.id == chapterId }
                if (index != -1) {
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
            readingRecord.readingBookChapterId =
                readingChapter?.id ?: readingRecord.readingBookChapterId
            DataManager.setReadingRecord(readingRecord)
        }
    }

    private val loadRecord = ConcurrentHashMap<Int, Deferred<Chapter>>()
    private suspend fun getChapterContent(chapterList: List<Chapter>?, chapterId: Int?) {

        if (chapterList.isNullOrEmpty()) {
            return
        }

        val chapterIndex = if (chapterId == null) {
            0
        } else {
            chapterList.indexOfFirst { it.id == chapterId }
        }
        (max(chapterIndex, 0) until (chapterIndex + 1)).map {
            val chapter = chapterList.getOrNull(chapterIndex)
            if (chapter != null) {
                if (chapter.isLoaded && chapter.content?.isNotEmpty() == true) {
                    null
                } else {
                    loadRecord[chapter.id] ?: async { DataManager.getChapterContent(chapter) }
                }
            } else {
                null
            }
        }.filterNotNull().awaitAll().forEach {
            loadRecord.remove(it.id)
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
            Log.e("onBindViewHolder pos:$position")
            val chapter = chapterList[position]
            holder.itemView.chapter_title.text = chapter.title
            if (chapter.content?.isNotEmpty() == true) {
                holder.itemView.chapter_content.setHtml(chapter.content!!)
            } else {
                holder.itemView.chapter_content.setHtml("拼命加载中……")
                activity.viewLaunch {
                    activity.getChapterContent(chapterList, chapter.id)
                    if (holder.adapterPosition == position && chapter.isLoaded) {
                        notifyDataSetChanged()
                        Log.e("pos:$position")
                    }
                }

            }
        }

        override fun getItemId(position: Int): Long {
            return chapterList[position].id.toLong()
        }
    }
}
