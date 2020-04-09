package com.sjianjun.reader.module.main.fragment


import android.os.Bundle
import android.view.View
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.module.reader.activity.BookReaderActivity
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import kotlinx.android.synthetic.main.item_text_text.view.*
import kotlinx.android.synthetic.main.main_fragment_book_chapter_list.*
import kotlinx.coroutines.flow.collectLatest


/**
 *展示章节列表
 */
class ChapterListFragment : BaseFragment() {
    val bookUrl by lazy { arguments!!.getString(BOOK_URL)!! }

    override fun getLayoutRes() = R.layout.main_fragment_book_chapter_list

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = ChapterListAdapter(this)
        chapterList.adapter = adapter
        viewLaunch {
            DataManager.getChapterList(bookUrl).collectLatest {
                adapter.data = it
                adapter.notifyDataSetChanged()
            }
        }
    }

    private class ChapterListAdapter(val fragment: ChapterListFragment) : BaseAdapter() {
        init {
            setHasStableIds(true)
        }

        var data = listOf<Chapter>()
        var readingChapterUrl = ""

        override fun getItemCount(): Int = data.size

        override fun itemLayoutRes(viewType: Int): Int {
            return R.layout.item_text_text
        }

        override fun onBindViewHolder(
            holder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
            position: Int
        ) {
            val c = data[position]
            holder.itemView.text1.text = c.title
            if (readingChapterUrl == c.url) {
                holder.itemView.text1.setTextColorRes(R.color.material_red_700)
            } else {
                holder.itemView.text1.setTextColorRes(R.color.material_textBlack_secondaryText)
            }
            if (c.isLoaded) {
                holder.itemView.mark.setBackgroundColor(R.color.material_green_A700.resColor())
            } else {
                holder.itemView.mark.setBackgroundColor(R.color.material_grey_500.resColor())
            }
            holder.itemView.setOnClickListener {
                fragment.startActivity<BookReaderActivity>(
                    BOOK_URL to c.bookUrl,
                    CHAPTER_URL to c.url
                )
            }
        }

        override fun getItemId(position: Int): Long {
            return data[position].url.id
        }
    }

}
