package com.sjianjun.reader.module.shelf

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.reader.BaseAsyncFragment
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.async.createAsyncLoadView
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.module.main.BookSourceListFragment
import com.sjianjun.reader.module.reader.activity.BookReaderActivity
import com.sjianjun.reader.popup.ErrorMsgPopup
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import com.sjianjun.reader.view.isLoading
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
import java.util.concurrent.atomic.AtomicInteger

class BookshelfFragment : BaseAsyncFragment() {
    private val bookList = ConcurrentHashMap<String, Book>()
    private val bookSyncErrorMap = ConcurrentHashMap<String, Throwable>()
    private val startingBookSyncErrorMap = ConcurrentHashMap<String, Throwable>()
    private lateinit var adapter: Adapter
    override fun getLayoutRes() = R.layout.main_fragment_book_shelf
    private lateinit var startingStationRefreshActor: SendChannel<List<Book>>

    override val onCreate: BaseAsyncFragment.() -> Unit = {
        startingStationRefreshActor = startingStationRefreshActor()

        setHasOptionsMenu(true)
        adapter = Adapter(this@BookshelfFragment)
        book_shelf_recycle_view.adapter = adapter
        initRefresh()
        initData()
    }

    override val onDestroy: BaseAsyncFragment.() -> Unit = {
        setHasOptionsMenu(false)
    }

    private fun initData() {
        launch {
            DataManager.getAllReadingBook().collectLatest {
                //书籍数据更新的时候必须重新创建 章节 书源 阅读数据的观察流
                bookList.clear()
                val bookNum = it.size
                book_shelf_refresh.max = bookNum
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
                        book.startingError = startingBookSyncErrorMap[book.key]

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


    private fun initRefresh() {
        book_shelf_swipe_refresh.setOnRefreshListener {
            launchIo {
                val sourceMap = mutableMapOf<String, MutableList<Book>>()

                bookList.values.forEach {
                    val list = sourceMap.getOrPut(it.source, { mutableListOf() })
                    list.add(it)
                }

                startingStationRefreshActor.offer(bookList.values.toList())
                book_shelf_refresh.progress = 0
                sourceMap.map {
                    async {
                        val script = it.value.firstOrNull()?.javaScriptList?.find { js ->
                            js.source == it.key
                        }
                        val delay = script?.getScriptField(JS_FIELD_REQUEST_DELAY) ?: 1000L
                        if (delay < 0) {
                            it.value.map {
                                async {
                                    val error = DataManager.reloadBookFromNet(it)
                                    if (error != null) {
                                        bookSyncErrorMap[it.key] = error
                                    } else {
                                        bookSyncErrorMap.remove(it.key)
                                    }
                                    book_shelf_refresh.progress = book_shelf_refresh.progress + 1
                                }
                            }.awaitAll()
                        } else {
                            it.value.apply { it.value.sortWith(bookComparator) }.forEach {
                                val error = DataManager.reloadBookFromNet(it)
                                if (error != null) {
                                    bookSyncErrorMap[it.key] = error
                                } else {
                                    bookSyncErrorMap.remove(it.key)
                                }
                                book_shelf_refresh.progress = book_shelf_refresh.progress + 1
                                delay(delay)
                            }
                        }
                    }
                }.awaitAll()
                withMain {
                    book_shelf_swipe_refresh?.isRefreshing = false
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun startingStationRefreshActor() =
        lifecycleScope.actor<List<Book>>(Dispatchers.IO, capacity = Channel.CONFLATED) {
            for (msg in channel) {
                showProgressBar()
                book_shelf_refresh?.secondaryProgress = 0
                val sourceMap = mutableMapOf<String, MutableList<Book>>()
                msg.mapNotNull {
                    val bookScript = it.javaScriptList?.find { script ->
                        script.source == it.source
                    }
                    val book = DataManager.getStartingBook(it, bookScript)
                    val delay = bookScript?.getScriptField<Long>(JS_FIELD_REQUEST_DELAY) ?: 1000
                    delay(delay)
                    book
                }.forEach {
                    val list = sourceMap.getOrPut(it.source, { mutableListOf() })
                    list.add(it)
                }

                val count = AtomicInteger()
                sourceMap.forEach { entry ->
                    val javaScript = DataManager.getJavaScript(entry.key)
                    val delay = javaScript?.getScriptField<Long>(JS_FIELD_REQUEST_DELAY) ?: 1000
                    if (delay < 0) {
                        entry.value.map {
                            async {
                                val error = DataManager.reloadBookFromNet(it, javaScript)
                                if (error != null) {
                                    startingBookSyncErrorMap[it.key] = error
                                } else {
                                    startingBookSyncErrorMap.remove(it.key)
                                }
                                book_shelf_refresh?.secondaryProgress = count.incrementAndGet()
                            }
                        }.awaitAll()
                    } else {
                        entry.value.forEach {
                            val error = DataManager.reloadBookFromNet(it, javaScript)
                            if (error != null) {
                                startingBookSyncErrorMap[it.key] = error
                            } else {
                                startingBookSyncErrorMap.remove(it.key)
                            }
                            book_shelf_refresh?.secondaryProgress = count.incrementAndGet()
                            delay(delay)
                        }
                    }

                }

                hideProgressBar()
            }
        }

    private suspend fun showProgressBar() = withMain {
        book_shelf_refresh?.animFadeIn()

    }

    private suspend fun hideProgressBar() = withMain {
        book_shelf_refresh?.animFadeOut()
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
                val error = book.error
                val startingError = book.startingError
                if (error == null && startingError == null) {
                    sync_error.hide()
                    sync_error.isClickable = false
                } else {
                    sync_error.imageTintList = if (error != null) {
                        ColorStateList.valueOf(R.color.material_reader_red_100.getColor())
                    } else {
                        ColorStateList.valueOf(R.color.material_reader_grey_700.getColor())
                    }
                    sync_error.show()
                    sync_error.setOnClickListener {
                        fragment.launchIo {
                            val popup = ErrorMsgPopup(fragment.context)
                                .init(
                                    "${error ?: startingError}\n" +
                                            "StackTrace:\n" +
                                            android.util.Log.getStackTraceString(
                                                error ?: startingError
                                            )
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

                setOnLongClickListener {
                    AlertDialog.Builder(context!!)
                        .setTitle("确认删除")
                        .setMessage("确定要删除《${book.title}》吗?")
                        .setPositiveButton("删除") { _, _ ->
                            fragment.bookList.remove(book.key)
                            data.removeAt(position)
                            notifyItemRemoved(position)
                            fragment.launchIo { DataManager.deleteBook(book) }
                        }
                        .show()
                    true
                }
            }

        }
    }
}