package com.sjianjun.reader.module.main

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BOOK_AUTHOR
import com.sjianjun.reader.BOOK_TITLE
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import com.sjianjun.reader.view.isLoading
import kotlinx.android.synthetic.main.main_fragment_book_source_list.*
import kotlinx.android.synthetic.main.main_item_fragment_book_source_list.view.*
import kotlinx.coroutines.launch


@Suppress("UNCHECKED_CAST")
class BookSourceListFragment : BaseFragment() {
    private val vm by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val bookTitle = requireArguments().getString(BOOK_TITLE)!!
                val bookAuthor = requireArguments().getString(BOOK_AUTHOR)!!
                return BookSourceListViewModel(bookTitle, bookAuthor) as T
//                return modelClass.getConstructor(String::class.java, String::class.java)
//                    .newInstance(bookAuthor, bookTitle)
            }
        }).get(BookSourceListViewModel::class.java)
    }

    private val adapter by lazy { BookListAdapter(this) }

    override fun getLayoutRes() = R.layout.main_fragment_book_source_list

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycle_view.adapter = adapter
        vm.bookList.observe(this) {
            adapter.data.clear()
            adapter.data.addAll(it)
            adapter.notifyDataSetChanged()
        }
        initRefresh()
    }

    private fun initRefresh() {

        swipe_refresh.setOnRefreshListener {
            vm.viewModelScope.launch {
                swipe_refresh.isRefreshing = false
                swipe_refresh.isEnabled = false
                vm.reloadAllBookFromNet()
                swipe_refresh.isEnabled = true
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
            holder.itemView.apply {
                bookCover.glide(fragment, book.cover)
                bookName.text = book.title
                author.text = "作者：${book.author}"
                lastChapter.text = "最新：${book.lastChapter?.title}"

                haveRead.text = "来源：${book.bookSource?.group}-${book.bookSource?.name}"
                loading.isLoading = book.isLoading

                if (book.readChapter?.content?.contentError == true) {
                    bv_unread.setHighlight(false)
                } else {
                    bv_unread.setHighlight(true)
                }
                if ((book.isLoading)) {
                    bv_unread.hide()
                } else {
                    bv_unread.show()
                }
                bv_unread.badgeCount = book.unreadChapterCount

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
