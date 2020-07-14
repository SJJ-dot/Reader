package com.sjianjun.reader.module.main

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.coroutine.flowIo
import com.sjianjun.reader.coroutine.launch
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import com.sjianjun.reader.view.isLoading
import kotlinx.android.synthetic.main.main_fragment_book_source_list.*
import kotlinx.android.synthetic.main.main_item_fragment_book_source_list.view.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlin.math.max
import kotlin.math.min


class BookSourceListFragment : BaseFragment() {

    private val bookTitle by lazy { requireArguments().getString(BOOK_TITLE)!! }
    private val bookAuthor by lazy { requireArguments().getString(BOOK_AUTHOR)!! }


    private val adapter by lazy {
        BookListAdapter(
            this
        )
    }

    override fun getLayoutRes() = R.layout.main_fragment_book_source_list

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycle_view.adapter = adapter

        launch {
            DataManager.getBookByTitleAndAuthor(bookTitle, bookAuthor)
                .map {
                    //并发读取章节列表
                    it.asFlow().flatMapMerge { book ->
                        flow {
                            book.lastChapter = DataManager.getLastChapterByBookUrl(book.url).first()
                            emit(book)
                        }
                    }.toList().sortedWith(bookComparator)
                }.flowIo().debounce(100).collect {
                    val size = it.size
                    if (adapter.data.size != size) {
                        swipe_refresh.layoutParams.height = max(min(size, 5), 1) * 90.dpToPx()
                        swipe_refresh.requestLayout()
                    }
                    adapter.data.clear()
                    adapter.data.addAll(it)
                    adapter.notifyDataSetChanged()
                }
        }

        initRefresh()
    }

    private fun initRefresh() {

        swipe_refresh.setOnRefreshListener {
            launch {
                swipe_refresh.isRefreshing = false
                swipe_refresh.isEnabled = false
                adapter.data.map {
                    async {
                        DataManager.reloadBookFromNet(it)
                    }
                }.awaitAll()
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
                if (book.lastChapter?.isLastChapter == false) {
                    red_dot.show()
                } else {
                    red_dot.hide()
                }
                haveRead.text = "来源：${book.source}"
                loading.isLoading = book.isLoading
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
                                val success = DataManager.deleteBookByUrl(book)
                                if (success == false) {
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
            return data[position].id
        }
    }

}
