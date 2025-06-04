package com.sjianjun.reader.module.main


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.*
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.databinding.ItemTextTextBinding
import com.sjianjun.reader.databinding.MainFragmentBookChapterListBinding
import com.sjianjun.reader.module.reader.activity.BookReaderActivity
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest


/**
 *展示章节列表
 */
class ChapterListFragment : BaseFragment() {
    val bookTitle: String get() = requireArguments().getString(BOOK_TITLE)!!

    private val adapter = ChapterListAdapter(this)
    var binding: MainFragmentBookChapterListBinding? = null

    override fun getLayoutRes() = R.layout.main_fragment_book_chapter_list
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.main_fragment_book_chapter_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = MainFragmentBookChapterListBinding.bind(view)
        binding?.recycleViewChapterList?.adapter = adapter
        initData()
    }

    private fun initData() {
        launch {
            DataManager.getReadingBook(bookTitle).flatMapLatest {
                if (it == null) {
                    emptyFlow<Pair<List<Chapter>, ReadingRecord>>()
                } else {
                    DataManager.getChapterList(it.id)
                        .combine(DataManager.getReadingRecord(it)) { chapterList, readingRecord ->
                            chapterList to readingRecord
                        }
                }
            }.debounce(300).collectLatest { (chapterList, readingRecord) ->
                adapter.readingRecord = readingRecord
                var change = adapter.readingChapterIndex != readingRecord?.chapterIndex
                change = change || (adapter.data.size != chapterList.size)
                adapter.readingChapterIndex = readingRecord?.chapterIndex ?: 0
                adapter.notifyDataSetDiff(chapterList) { o, n ->
                    o.index == n.index && o.bookId == n.bookId
                }
                if (change) {
                    val index = adapter.data.indexOfFirst {
                        it.index == adapter.readingChapterIndex
                    }
                    binding?.recycleViewChapterList?.scrollToPosition(index)
                }
            }
        }
    }

    private class ChapterListAdapter(val fragment: ChapterListFragment) : BaseAdapter<Chapter>() {
        init {
            setHasStableIds(true)
        }

        var readingChapterIndex = 0
        var readingRecord: ReadingRecord? = null


        override fun itemLayoutRes(viewType: Int): Int {
            return R.layout.item_text_text
        }

        override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
            val binding = ItemTextTextBinding.bind(holder.itemView)
            holder.itemView.apply {
                val c = data[position]
                binding.text1.text = c.title
                if (readingChapterIndex == c.index) {
                    binding.text1.setTextColorRes(R.color.mdr_green_500)
                } else {
                    binding.text1.setTextColorRes(R.color.dn_text_color_light)
                }
                if (c.isLoaded) {
                    binding.mark.setBackgroundColor(R.color.mdr_green_A700.color(context))
                } else {
                    binding.mark.setBackgroundColor(R.color.mdr_grey_500.color(context))
                }
                setOnClickListener {
                    if (readingChapterIndex == c.index) {
                        //如果是当前章节，直接返回
                        return@setOnClickListener
                    }
                    fragment.launch {
                        //如果不是当前章节，更新阅读记录
                        readingRecord?.let {
                            it.chapterIndex = c.index
                            it.offest = 0
                            it.isEnd = false
                            it.updateTime = System.currentTimeMillis()
                            DataManager.setReadingRecord(it)
                        }
                        fragment.startActivity<BookReaderActivity>(BOOK_ID to c.bookId)

                    }
                }
            }

        }

        override fun getItemId(position: Int): Long {
            return data[position].index.toLong()
        }
    }

}
