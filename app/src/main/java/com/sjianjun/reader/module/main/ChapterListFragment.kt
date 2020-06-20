package com.sjianjun.reader.module.main


import android.os.Bundle
import android.view.View
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.coroutine.launch
import com.sjianjun.reader.module.reader.activity.BookReaderActivity
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import kotlinx.android.synthetic.main.item_text_text.view.*
import kotlinx.android.synthetic.main.main_fragment_book_chapter_list.*
import kotlinx.coroutines.flow.*


/**
 *展示章节列表
 */
class ChapterListFragment : BaseFragment() {
    val bookTitle: String
        get() = arguments!!.getString(BOOK_TITLE)!!

    val bookAuthor: String
        get() = arguments!!.getString(BOOK_AUTHOR)!!

    private val adapter =
        ChapterListAdapter(
            this
        )
    override fun getLayoutRes() = R.layout.main_fragment_book_chapter_list

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        recycle_view_chapter_list.adapter = adapter

        initData()
    }

    private fun initData() {
        launch {
            DataManager.getReadingBook(bookTitle, bookAuthor).flatMapLatest {
                if (it == null) {
                    emptyFlow<Pair<List<Chapter>, ReadingRecord>>()
                } else {
                    DataManager.getChapterList(it.url)
                        .combine(DataManager.getReadingRecord(it)) { chapterList, readingRecord ->
                            chapterList to readingRecord
                        }
                }
            }.collectLatest { (chapterList, readingRecord) ->
                val change = adapter.readingChapterUrl != readingRecord?.chapterUrl ?: ""
                adapter.readingChapterUrl = readingRecord?.chapterUrl ?: ""
                adapter.data = chapterList
                adapter.notifyDataSetChanged()
                if (change) {
                    val index = adapter.data.indexOfFirst {
                        it.url == adapter.readingChapterUrl
                    }
                    recycle_view_chapter_list.scrollToPosition(index)
                }
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
                holder.itemView.text1.setTextColorRes(R.color.material_reader_green_500)
            } else {
                holder.itemView.text1.setTextColorRes(R.color.day_night_text_color_light)
            }
            if (c.isLoaded) {
                holder.itemView.mark.setBackgroundColor(R.color.material_reader_green_A700.getColor())
            } else {
                holder.itemView.mark.setBackgroundColor(R.color.material_reader_grey_500.getColor())
            }
            holder.itemView.setOnClickListener {
                fragment.startActivity<BookReaderActivity>(
                    BOOK_URL to c.bookUrl,
                    CHAPTER_URL to c.url
                )
            }
        }

        override fun getItemId(position: Int): Long {
            return data[position].id
        }
    }

}
