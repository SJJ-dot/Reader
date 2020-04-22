package com.sjianjun.reader.module.main.fragment

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.module.reader.activity.BookReaderActivity
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import com.sjianjun.reader.view.isLoading
import kotlinx.android.synthetic.main.item_book_list.view.*
import kotlinx.android.synthetic.main.main_fragment_book_shelf.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

class BookshelfFragment : BaseFragment() {
    private val bookList = mutableMapOf<String, Book>()
    private val bookSyncErrorList = mutableMapOf<String, Throwable>()
    private lateinit var adapter: Adapter
    override fun getLayoutRes() = R.layout.main_fragment_book_shelf
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        adapter = Adapter(this)
        recycle_view.adapter = adapter

        swipe_refresh.setOnRefreshListener {
            launchIo {
                val sourceMap = mutableMapOf<String, MutableList<Book>>()
                bookList.values.forEach {
                    val list = sourceMap.getOrPut(it.source, { mutableListOf() })
                    list.add(it)
                }
                sourceMap.values.map {
                    async {
                        it.apply { it.sortWith(bookComparator) }.forEach {
                            val qiDian = async { DataManager.updateOrInsertStarting(it.url) }
                            DataManager.reloadBookFromNet(it.url)
                            delay(1000)
                            qiDian.await()
                        }
                    }
                }.awaitAll()
                withMain {
                    swipe_refresh?.isRefreshing = false
                }
            }
        }

        val mItemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                return makeMovementFlags(0, ItemTouchHelper.LEFT)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                launch {
                    val pos = viewHolder.adapterPosition
                    val book = adapter.data.getOrNull(pos) ?: return@launch
                    bookList.remove(book.key)
                    adapter.data.remove(book)
                    adapter.notifyItemRemoved(pos)
                    DataManager.deleteBook(book)
                }
            }

        })
        mItemTouchHelper.attachToRecyclerView(recycle_view)

        launch {
            DataManager.getAllReadingBook().collectLatest {
                //书籍数据更新的时候必须重新创建 章节 书源 阅读数据的观察流
                val bookNum = it.size
                it.asFlow().flatMapMerge { book ->
                    combine(
                        DataManager.getReadingRecord(book).map { record ->
                            val id = record?.chapterUrl ?: return@map null
                            DataManager.getChapterByUrl(id).first() to record
                        },
                        DataManager.getLastChapterByBookUrl(book.url),
                        DataManager.getJavaScript(book.title, book.author)
                    ) { readChapter, lastChapter, js ->
                        book.record = readChapter?.second
                        book.readChapter = readChapter?.first
                        book.lastChapter = lastChapter
                        book.javaScriptList = js

                        val lastChapterIndex = book.lastChapter?.index ?: 0
                        val readChapterIndex = book.readChapter?.index ?: 0
                        book.unreadChapterCount = if (book.record?.isEnd == true) {
                            lastChapterIndex - readChapterIndex
                        } else {
                            lastChapterIndex - readChapterIndex + 1
                        }

                        book
                    }
                }.map { book ->
                    bookList[book.key] = book
                    bookList.values.sortedWith(bookComparator)
                }.flowIo().collectLatest { list ->
                    if (list.size == bookNum) {
                        adapter.data.clear()
                        adapter.data.addAll(list)
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main_fragment_book_shelf_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.search_book_shelf -> {
                NavHostFragment.findNavController(this).navigate(R.id.searchFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private class Adapter(val fragment: BookshelfFragment) : BaseAdapter() {
        init {
            setHasStableIds(true)
        }

        val data = mutableListOf<Book>()

        override fun itemLayoutRes(viewType: Int) = R.layout.item_book_list

        override fun getItemCount(): Int = data.size

        override fun getItemId(position: Int): Long {
            return data[position].id
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val book = data[position]
            holder.setRecyclable(!book.isLoading)
            holder.itemView.apply {
                bookCover.glide(fragment, book.cover)
                bookName.text = book.title
                author.text = "作者：${book.author}"
                lastChapter.text = "最新：${book.lastChapter?.title}"
                haveRead.text = "已读：${book.readChapter?.title ?: "未开始阅读"}"
                loading.isLoading = book.isLoading


                if (book.lastChapter?.isLastChapter == false) {
                    bv_unread.setHighlight(false)
                } else {
                    bv_unread.setHighlight(true)
                }
                if ((book.isLoading || book.unreadChapterCount <= 0) && book.lastChapter?.isLastChapter != false) {
                    bv_unread.hide()
                } else {
                    bv_unread.show()
                }
                bv_unread.badgeCount = book.unreadChapterCount

                origin.text = "来源：${book.source}共${book.javaScriptList?.size}个源"
                origin.setOnClickListener {
                    fragmentCreate<BookSourceListFragment>(
                        BOOK_TITLE to book.title,
                        BOOK_AUTHOR to book.author
                    ).show(fragment.childFragmentManager, "BookSourceListFragment")
                }

                bookCover.setOnClickListener {
                    fragment.findNavController()
                        .navigate(
                            R.id.bookDetailsFragment,
                            bundle(BOOK_TITLE to book.title, BOOK_AUTHOR to book.author)
                        )
                }

                setOnClickListener {
                    fragment.startActivity<BookReaderActivity>(BOOK_URL, book.url)
                }
            }

        }
    }
}