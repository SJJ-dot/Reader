package com.sjianjun.reader.module.shelf

import android.os.Bundle
import android.view.*
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.module.main.BookSourceListFragment
import com.sjianjun.reader.module.reader.activity.BookReaderActivity
import com.sjianjun.reader.popup.ErrorMsgPopup
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import com.sjianjun.reader.view.setLoading
import kotlinx.android.synthetic.main.item_book_list.view.*
import kotlinx.android.synthetic.main.main_fragment_book_shelf.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

class BookshelfFragment : BaseFragment() {
    private val bookList = ConcurrentHashMap<String, Book>()
    private val bookSyncErrorMap = ConcurrentHashMap<String, Throwable>()
    private lateinit var adapter: Adapter
    override fun getLayoutRes() = R.layout.main_fragment_book_shelf
    private lateinit var startingStationRefreshActor: SendChannel<List<Book>>
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        startingStationRefreshActor = startingStationRefreshActor()

        setHasOptionsMenu(true)
        adapter = Adapter(this)
        book_shelf_recycle_view.adapter = adapter

        book_shelf_swipe_refresh.setOnRefreshListener {
            launchIo {
                val sourceMap = mutableMapOf<String, MutableList<Book>>()

                bookList.values.forEach {
                    val list = sourceMap.getOrPut(it.source, { mutableListOf() })
                    list.add(it)
                }

                startingStationRefreshActor.offer(bookList.values.toList())

                sourceMap.values.map {
                    async {
                        it.apply { it.sortWith(bookComparator) }.forEach {
                            val error = DataManager.reloadBookFromNet(it.url)
                            if (error != null) {
                                bookSyncErrorMap[it.key] = error
                            } else {
                                bookSyncErrorMap.remove(it.key)
                            }
                            delay(1000)
                            it.key to error
                        }
                    }
                }.awaitAll()
                withMain {
                    book_shelf_swipe_refresh?.isRefreshing = false
                    adapter.notifyDataSetChanged()
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
        mItemTouchHelper.attachToRecyclerView(book_shelf_recycle_view)

        launch {
            DataManager.getAllReadingBook().collectLatest {
                //书籍数据更新的时候必须重新创建 章节 书源 阅读数据的观察流
                bookList.clear()
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

                        book.error = bookSyncErrorMap[book.key]

                        book
                    }
                }.mapNotNull { book ->
                    bookList[book.key] = book
                    if (bookList.size == bookNum) {
                        bookList.values.sortedWith(bookComparator)
                    } else {
                        null
                    }
                }.flowIo().collectLatest { list ->
                    adapter.data.clear()
                    adapter.data.addAll(list)
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun startingStationRefreshActor() =
        lifecycleScope.actor<List<Book>>(Dispatchers.IO, capacity = Channel.CONFLATED) {
            for (msg in channel) {
                withMain {
                    book_shelf_refresh.isAutoLoading = true
                }
                delay(1000)
                msg.forEach {
                    DataManager.updateOrInsertStarting(it.url)
                    delay(1000)
                }

                withMain {
                    book_shelf_refresh.isAutoLoading = false
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
                loading.setLoading(book.isLoading)
                val error = book.error
                if (error == null) {
                    sync_error.hide()
                    sync_error.isClickable = false
                } else {
                    sync_error.show()
                    sync_error.setOnClickListener {
                        fragment.launchIo {

                            val popup = ErrorMsgPopup(fragment.context)
                                .init(
                                    """
                                    $error
                                    StackTrace:
                                    ${android.util.Log.getStackTraceString(error)}
                                """.trimIndent()
                                )
                                .setPopupGravity(Gravity.TOP or Gravity.START)

                            withMain {
                                popup.showPopupWindow(it)
                            }
                        }
                    }
                }

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