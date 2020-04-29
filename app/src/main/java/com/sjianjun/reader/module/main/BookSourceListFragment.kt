package com.sjianjun.reader.module.main

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import com.sjianjun.reader.view.isLoading
import kotlinx.android.synthetic.main.main_fragment_book_source_list.*
import kotlinx.android.synthetic.main.main_item_fragment_book_source_list.view.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*


class BookSourceListFragment : BaseFragment() {

    private val bookTitle by lazy { arguments!!.getString(BOOK_TITLE)!! }
    private val bookAuthor by lazy { arguments!!.getString(BOOK_AUTHOR)!! }


    private val adapter by lazy {
        BookListAdapter(
            this
        )
    }

    override fun getLayoutRes() = R.layout.main_fragment_book_source_list

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycle_view.adapter = adapter

        val mItemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                if (adapter.data.size > 1) {
                    return makeMovementFlags(0, ItemTouchHelper.LEFT)
                }
                return makeMovementFlags(0, 0)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val adapterPosition = viewHolder.adapterPosition
                launch {
                    val book = adapter.data.getOrNull(adapterPosition)
                    val success = DataManager.deleteBookByUrl(book ?: return@launch)
                    if (success == false) {
                        toast("删除失败")
                    } else {
                        val index = adapter.data.indexOf(book)
                        if (index != -1) {
                            adapter.data.removeAt(index)
                            adapter.notifyItemRemoved(index)
                        }
                    }
                }
            }
        })
        mItemTouchHelper.attachToRecyclerView(recycle_view)

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
                }.flowIo().collectLatest {
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
                source_refresh.animFadeIn()
                source_refresh.progress = 0
                adapter.data.map {
                    async {
                        DataManager.reloadBookFromNet(it)
                        source_refresh.progress = source_refresh.progress + 1
                    }
                }.awaitAll()
                source_refresh.animFadeOut()
                swipe_refresh.isEnabled = true
            }
        }
    }

    class BookListAdapter(val fragment: BookSourceListFragment) : BaseAdapter() {
        init {
            setHasStableIds(true)
        }

        val data: MutableList<Book> = mutableListOf()
        override fun getItemCount() = data.size

        override fun itemLayoutRes(viewType: Int) = R.layout.main_item_fragment_book_source_list

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val book = data[position]
            holder.setRecyclable(!book.isLoading)
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
            }
        }

        override fun getItemId(position: Int): Long {
            return data[position].id
        }
    }

}
