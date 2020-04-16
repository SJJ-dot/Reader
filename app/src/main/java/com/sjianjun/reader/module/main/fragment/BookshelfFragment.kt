package com.sjianjun.reader.module.main.fragment

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.MutableLiveData
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

    private val bookList = MutableLiveData<List<Book>>()
    private lateinit var adapter: Adapter
    override fun getLayoutRes() = R.layout.main_fragment_book_shelf
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        adapter = Adapter(this)
        recycle_view.adapter = adapter

        bookList.observeViewLifecycle {
            adapter.data.clear()
            adapter.data.addAll(it)
            adapter.notifyDataSetChanged()
        }

        swipe_refresh.setOnRefreshListener {
            viewLaunch {
                val sourceMap = mutableMapOf<String, MutableList<Book>>()
                bookList.value?.forEach {
                    val list = sourceMap.getOrPut(it.source, { mutableListOf() })
                    list.add(it)
                }
                sourceMap.values.map {
                    async {
                        it.forEach {
                            val qiDian = async { DataManager.updateOrInsertQiDianBook(it.url) }
                            DataManager.reloadBookFromNet(it.url)
                            delay(1000)
                            qiDian.await()
                        }
                    }
                }.awaitAll()
                swipe_refresh?.isRefreshing = false
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
                viewLaunch {
                    val book = adapter.data.getOrNull(viewHolder.adapterPosition)
                    DataManager.deleteBook(book ?: return@viewLaunch)
                }
            }

        })
        mItemTouchHelper.attachToRecyclerView(recycle_view)

        viewLaunch {
            //需要最新章节 阅读章节 书源数量
            DataManager.getAllReadingBook().collectLatest {
                //书籍数据更新的时候必须重新创建 章节 书源 阅读数据的观察流
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
                        book
                    }
                }.flowIo().collectLatest { _ ->
                    bookList.postValue(it)
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
            return data[position].url.id
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val book = data[position]
            holder.itemView.apply {
                bookCover.glide(fragment, book.cover)
                bookName.text = book.title
                author.text = "作者：${book.author}"
                lastChapter.text = "最新：${book.lastChapter?.title}"
                haveRead.text = "已读：${book.readChapter?.title ?: "未开始阅读"}"
                loading.isLoading = book.isLoading

                val lastChapterIndex = book.lastChapter?.index ?: 0
                val readChapterIndex = book.readChapter?.index ?: 0
                val remainingCount = if (book.record?.isEnd == true) {
                    lastChapterIndex - readChapterIndex
                } else {
                    lastChapterIndex - readChapterIndex + 1
                }
                if (book.isLoading || remainingCount <= 0) {
                    bv_unread.hide()
                } else {
                    bv_unread.show()
                    bv_unread.badgeCount = remainingCount
                }

                origin.text = "来源：${book.source}共${book.javaScriptList?.size}个源"
                origin.setOnClickListener {
                    fragmentCreate<BookSourceListFragment>(
                        BOOK_TITLE to book.title,
                        BOOK_AUTHOR to book.author
                    ).show(fragment.childFragmentManager, "BookSourceListFragment")
                }

                bookCover.setOnClickListener{
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