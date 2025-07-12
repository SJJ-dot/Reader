package com.sjianjun.reader.module.main


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.*
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.databinding.ItemTextTextBinding
import com.sjianjun.reader.databinding.MainFragmentBookChapterListBinding
import com.sjianjun.reader.module.reader.activity.BookReaderActivity
import com.sjianjun.reader.utils.*
import com.sjianjun.reader.view.click


/**
 *展示章节列表
 */
class ChapterListFragment : BaseFragment() {
    val bookTitle: String get() = requireArguments().getString(BOOK_TITLE)!!
    private val adapter = ChapterListAdapter(this)
    private var binding: MainFragmentBookChapterListBinding? = null
    private val viewModel by viewModels<ChapterListViewModel>()

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
        viewModel.init(bookTitle)
        viewModel.chapterListLiveData.observeViewLifecycle {
            adapter.data.clear()
            adapter.data.addAll(it)
            adapter.notifyDataSetChanged()
            val index = adapter.data.indexOfFirst {
                it.index == adapter.readingChapterIndex
            }
            if (index > 0)
                binding?.recycleViewChapterList?.scrollToPosition(index)

        }
        viewModel.readingRecord.observeViewLifecycle {
            adapter.readingRecord = it
            adapter.readingChapterIndex = it?.chapterIndex ?: 0
            val index = adapter.data.indexOfFirst {
                it.index == adapter.readingChapterIndex
            }
            if (index > 0)
                binding?.recycleViewChapterList?.scrollToPosition(index)
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

        override fun onBindViewHolder(
            holder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
            position: Int
        ) {
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
                click {
                    if (readingChapterIndex == c.index) {
                        //如果是当前章节，直接返回
                        return@click
                    }
                    fragment.launch {
                        //如果不是当前章节，更新阅读记录
                        fragment.viewModel.saveRecord(c)
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
