package com.sjianjun.reader.module.reader.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ImmersionBar
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.BOOK_ID
import com.sjianjun.reader.utils.CHAPTER_ID
import kotlinx.android.synthetic.main.activity_book_reader.*
import kotlinx.android.synthetic.main.reader_item_activity_chapter_content.view.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onEach
import sjj.alog.Log
import kotlin.math.max

class BookReaderActivity : BaseActivity() {
    private val bookId by lazy { intent.getStringExtra(BOOK_ID)!!.toInt() }
    private val chapterId by lazy { intent.getStringExtra(CHAPTER_ID)?.toInt() }
    private val adapter by lazy { ChapterListAdapter(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_reader)
        appBarLayout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, offset ->
            if (offset + appBarLayout.height == 0) {
                ImmersionBar.with(this).hideBar(BarHide.FLAG_HIDE_STATUS_BAR).init()
            } else if (offset == 0) {
                ImmersionBar.with(this).hideBar(BarHide.FLAG_SHOW_BAR).init()
            }
            Log.e(offset)
        })
        //先不显示
        drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        recycle_view.adapter = adapter
        viewLaunch {
            DataManager.getChapterList(bookId).onEach {
                val chapter = it.find { chapterId == it.id } ?: it.firstOrNull()
                getChapterContent(it, chapterId)
            }.collectLatest {
                adapter.chapterList = it
                adapter.notifyDataSetChanged()
                val chapterId = chapterId
                if (chapterId != null) {
                    val index = it.indexOfFirst { it.id == chapterId }
                    if (index != -1) {
                        recycle_view.scrollToPosition(index)
                    }
                }
            }
        }

    }

    private suspend fun getChapterContent(chapterList: List<Chapter>, chapterId: Int?) {
        val chapterIndex = if (chapterId == null) {
            0
        } else {
            chapterList.indexOfFirst { it.id == chapterId }
        }
        (max(chapterIndex, 0) until (chapterIndex + 1)).forEach {
            val chapter = chapterList.getOrNull(chapterIndex)
            if (chapter != null) {
                if (chapter.isLoaded && chapter.content?.isNotEmpty() == true) {
                    DataManager.getChapterContent(chapter)
                }
            }
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
                        notifyItemChanged(position)
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
