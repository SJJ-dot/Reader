package com.sjianjun.reader.module.main

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BOOK_TITLE
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.databinding.MainFragmentBookSourceListBinding
import com.sjianjun.reader.databinding.MainItemFragmentBookSourceListBinding
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import com.sjianjun.reader.view.isLoading
import kotlinx.coroutines.launch


@Suppress("UNCHECKED_CAST")
class BookSourceListFragment : BaseFragment() {
    var binding: MainFragmentBookSourceListBinding? = null
    private val vm by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val bookTitle = requireArguments().getString(BOOK_TITLE)!!
                return BookSourceListViewModel(bookTitle) as T
            }
        }).get(BookSourceListViewModel::class.java)
    }

    private val adapter by lazy { BookListAdapter(this) }

    override fun getLayoutRes() = R.layout.main_fragment_book_source_list

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = MainFragmentBookSourceListBinding.bind(view)
        binding?.recycleView?.adapter = adapter
        vm.bookList.observe(this) {
            adapter.data.clear()
            adapter.data.addAll(it)
            adapter.notifyDataSetChanged()
        }
        initRefresh()
    }

    private fun initRefresh() {

        binding?.swipeRefresh?.setOnRefreshListener {
            vm.viewModelScope.launch {
                binding?.swipeRefresh?.isRefreshing = false
                binding?.swipeRefresh?.isEnabled = false
                vm.reloadAllBookFromNet()
                binding?.swipeRefresh?.isEnabled = true
            }
        }
    }

    class BookListAdapter(val fragment: BookSourceListFragment) : BaseAdapter<Book>() {
        init {
            setHasStableIds(true)
        }

        override fun itemLayoutRes(viewType: Int) = R.layout.main_item_fragment_book_source_list

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val book = data[position]
//            holder.setRecyclable(!book.isLoading)
            val binding = MainItemFragmentBookSourceListBinding.bind(holder.itemView)
            holder.itemView.apply {
                binding.bookCover.glide(book.cover)
                binding.bookName.text = book.title
                binding.author.text = "作者：${book.author}"
                binding.lastChapter.text = "最新：${book.lastChapter?.title}"

                binding.haveRead.text = "来源：${book.bookSource?.group}-${book.bookSource?.name}"
                binding.loading.isLoading = book.isLoading

                if (book.readChapter?.content?.contentError == true) {
                    binding.bvUnread.setHighlight(false)
                } else {
                    binding.bvUnread.setHighlight(true)
                }
                if ((book.isLoading)) {
                    binding.bvUnread.hide()
                } else {
                    binding.bvUnread.show()
                }
                binding.bvUnread.badgeCount = book.unreadChapterCount

                setOnClickListener {
                    fragment.launch {
                        DataManager.changeReadingRecordBookSource(book)
                        fragment.dismissAllowingStateLoss()
                    }

                }
                setOnLongClickListener {
                    AlertDialog.Builder(context!!)
                        .setTitle("确认删除")
                        .setMessage("确定要删除《${book.title}》吗?")
                        .setPositiveButton("删除") { _, _ ->
                            fragment.launch {
                                val success = DataManager.deleteBookById(book)
                                if (!success) {
                                    toast("删除失败")
                                } else {
                                    data.removeAt(position)
                                    notifyItemRemoved(position)
                                }
                            }
                        }
                        .show()
                    true
                }
            }
        }

        override fun getItemId(position: Int): Long {
            return data[position].id.id
        }
    }

}
