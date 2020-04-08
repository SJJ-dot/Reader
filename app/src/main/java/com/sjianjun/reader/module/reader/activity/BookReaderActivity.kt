package com.sjianjun.reader.module.reader.activity

import android.app.ActionBar
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.BOOK_ID
import com.sjianjun.reader.utils.CHAPTER_ID
import kotlinx.android.synthetic.main.activity_book_reader.*
import kotlinx.android.synthetic.main.reader_item_activity_chapter_content.view.*
import kotlinx.coroutines.flow.collectLatest
import sjj.alog.Log

class BookReaderActivity : BaseActivity() {
    private val bookId by lazy { intent.getStringExtra(BOOK_ID)!!.toInt() }
    private val chapterId by lazy { intent.getStringExtra(CHAPTER_ID)?.toInt() }
    private val adapter by lazy { ChapterListAdapter(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_reader)
        appBarLayout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, offset ->
            Log.e(offset)
        })
        //先不显示
        drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        recycle_view.adapter = adapter
        viewLaunch {
            DataManager.getChapterList(bookId).collectLatest {
                adapter.chapterList = it
                adapter.notifyDataSetChanged()
                val  chapterId = chapterId
                if (chapterId != null) {
                    val index = it.indexOfFirst { it.id == chapterId }
                    if (index != -1) {
                        recycle_view.scrollToPosition(index)
                    }
                }
            }
        }

    }

    class ChapterListAdapter(val activity: BookReaderActivity) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {
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
            if (chapter.isLoaded) {
                holder.itemView.layoutParams.height = ActionBar.LayoutParams.WRAP_CONTENT
            } else {
                holder.itemView.layoutParams.height = ActionBar.LayoutParams.MATCH_PARENT
            }
            holder.itemView.chapter_title.text = chapter.title
            if (chapter.content?.isNotEmpty() == true) {
                holder.itemView.chapter_content.text = Html.fromHtml(chapter.content,Html.FROM_HTML_MODE_COMPACT)
            } else {
                holder.itemView.chapter_content.text = "拼命加载中……"
                activity.viewLaunch {
                    DataManager.getChapterContent(chapter)
                    if (holder.adapterPosition == position && chapter.content?.isNotEmpty() == true) {
                        holder.itemView.chapter_content.text = Html.fromHtml(chapter.content,Html.FROM_HTML_MODE_COMPACT)
                        holder.itemView.layoutParams.height = ActionBar.LayoutParams.WRAP_CONTENT
                        holder.itemView.requestLayout()
//                        notifyItemChanged(position)
                    }
                }

            }
        }
    }
}
