package com.sjianjun.reader.module.main


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.sjianjun.async.AsyncView
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.module.reader.activity.BookReaderActivity
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import kotlinx.android.synthetic.main.item_text_text.view.*
import kotlinx.android.synthetic.main.main_fragment_book_chapter_list.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import sjj.alog.Log


/**
 *展示章节列表
 */
class ChapterListFragment : BaseFragment() {
    val bookTitle: String get() = requireArguments().getString(BOOK_TITLE)!!

    val bookAuthor: String get() = requireArguments().getString(BOOK_AUTHOR)!!

    private val adapter = ChapterListAdapter(this)

    override fun getLayoutRes() = R.layout.main_fragment_book_chapter_list
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return AsyncView(requireContext(), R.layout.main_fragment_book_chapter_list) {
            recycle_view_chapter_list.adapter = adapter
            initData()
        }
    }

    private fun initData() {
        launch {
            DataManager.getReadingBook(bookTitle, bookAuthor).flatMapLatest {
                if (it == null) {
                    emptyFlow<Pair<List<Chapter>, ReadingRecord>>()
                } else {
                    DataManager.getChapterList(it.id)
                        .combine(DataManager.getReadingRecord(it)) { chapterList, readingRecord ->
                            chapterList to readingRecord
                        }
                }
            }.debounce(300).collectLatest { (chapterList, readingRecord) ->
                var change = adapter.readingChapterIndex != readingRecord?.chapterIndex
                change = change || (adapter.data.size != chapterList.size)
                adapter.readingChapterIndex = readingRecord?.chapterIndex ?: 0
                adapter.data.clear()
                adapter.data.addAll(chapterList)
                adapter.notifyDataSetChanged()
                if (change) {
                    val index = adapter.data.indexOfFirst {
                        it.index == adapter.readingChapterIndex
                    }
                    recycle_view_chapter_list.scrollToPosition(index)
                }
            }
        }
    }

    private class ChapterListAdapter(val fragment: ChapterListFragment) : BaseAdapter<Chapter>() {
        init {
            setHasStableIds(true)
        }

        var readingChapterIndex = 0


        override fun itemLayoutRes(viewType: Int): Int {
            return R.layout.item_text_text
        }

        override fun onBindViewHolder(
            holder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
            position: Int
        ) {
            holder.itemView.apply {
                val c = data[position]
                text1.text = c.title
                if (readingChapterIndex == c.index) {
                    text1.setTextColorRes(R.color.mdr_green_500)
                } else {
                    text1.setTextColorRes(R.color.dn_text_color_light)
                }
                if (c.isLoaded) {
                    mark.setBackgroundColor(R.color.mdr_green_A700.color(context))
                } else {
                    mark.setBackgroundColor(R.color.mdr_grey_500.color(context))
                }
                setOnClickListener {
                    fragment.startActivity<BookReaderActivity>(
                        BOOK_ID to c.bookId,
                        CHAPTER_INDEX to c.index
                    )
                }
            }

        }

        override fun getItemId(position: Int): Long {
            return data[position].index.toLong()
        }
    }

}
