package com.sjianjun.reader.module.shelf

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.sjianjun.coroutine.flowIo
import com.sjianjun.coroutine.launch
import com.sjianjun.coroutine.launchIo
import com.sjianjun.coroutine.withMain
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseViewAdapter
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.module.main.BookSourceListFragment
import com.sjianjun.reader.module.reader.activity.BookReaderActivity
import com.sjianjun.reader.popup.ErrorMsgPopup
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.repository.JsManager
import com.sjianjun.reader.utils.*
import com.sjianjun.reader.view.isLoading
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@FlowPreview
class BookshelfFragment : BaseFragment() {
    private val bookList = ConcurrentHashMap<String, Book>()
    private lateinit var adapter: Adapter
    private lateinit var bookshelfUi: BookshelfUi
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        bookshelfUi = BookshelfUi(requireContext())
        return bookshelfUi.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        adapter = Adapter(this@BookshelfFragment)
        bookshelfUi.recyclerViw.adapter = adapter
        initRefresh()
        initData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        setHasOptionsMenu(false)
    }

    private fun initData() {
        launch {
            DataManager.getAllReadingBook().collectLatest {
                //书籍数据更新的时候必须重新创建 章节 书源 阅读数据的观察流
                bookList.clear()
                val bookNum = it.size
                bookshelfUi.loading.max = bookNum
                it.asFlow().flatMapMerge { book ->
                    combine(
                        DataManager.getReadingRecord(book).map { record ->
                            val id = record?.chapterUrl ?: return@map null
                            DataManager.getChapterByUrl(id).first() to record
                        },
                        DataManager.getLastChapterByBookUrl(book.url)
                    ) { readChapter, lastChapter ->
                        val js = JsManager.getAllBookJs(book.title, book.author)
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

                        val bookScript = js.find { script ->
                            script.source == book.source
                        }
                        val startingBook =
                            DataManager.getStartingBook(book, bookScript, onlyLocal = true)
                        book.startingError = startingBook?.error

                        book
                    }
                }.mapNotNull { book ->
                    bookList[book.key] = book
                    if (bookList.size == bookNum) {
                        bookList.values.sortedWith(bookComparator)
                    } else {
                        null
                    }
                }.flowIo().debounce(100).collect { list ->
                    adapter.data.clear()
                    adapter.data.addAll(list)
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }


    private fun initRefresh() {
        bookshelfUi.rootRefresh.setOnRefreshListener {
            launchIo {
                val sourceMap = mutableMapOf<String, MutableList<Book>>()

                bookList.values.forEach {
                    val list = sourceMap.getOrPut(it.source, { mutableListOf() })
                    list.add(it)
                }
                startingStationRefreshActor(bookList.values.toList())

                showProgressBar(SHOW_FLAG_REFRESH)
                bookshelfUi.loading.progress = 0
                sourceMap.map {
                    async {
                        val script = it.value.firstOrNull()?.javaScriptList?.find { js ->
                            js.source == it.key
                        }
                        val delay = script?.getScriptField(JS_FIELD_REQUEST_DELAY) ?: 1000L
                        if (delay < 0) {
                            it.value.map {
                                async {
                                    DataManager.reloadBookFromNet(it)
                                    bookshelfUi.loading.progress = bookshelfUi.loading.progress + 1
                                }
                            }.awaitAll()
                        } else {
                            it.value.apply { it.value.sortWith(bookComparator) }.forEach {
                                DataManager.reloadBookFromNet(it)
                                bookshelfUi.loading.progress = bookshelfUi.loading.progress + 1
                                delay(delay)
                            }
                        }
                    }
                }.awaitAll()
                withMain {
                    hideProgressBar(SHOW_FLAG_REFRESH)
                    bookshelfUi.rootRefresh.isRefreshing = false
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun startingStationRefreshActor(msg: List<Book>) =
        launch(singleCoroutineKey = "startingStationRefreshActor") {
            showProgressBar(SHOW_FLAG_STARTING_STATION)
            bookshelfUi.loading.secondaryProgress = 0
            val sourceMap = mutableMapOf<String, MutableList<Book>>()
            msg.map {
                async {
                    val bookScript = it.javaScriptList?.find { script ->
                        script.source == it.source
                    }
                    val book = DataManager.getStartingBook(it, bookScript)
                    if (book == it) {
                        null
                    } else {
                        val delay = bookScript?.getScriptField<Long>(JS_FIELD_REQUEST_DELAY)
                        delay(delay ?: 1000)
                        book
                    }
                }
            }.awaitAll().filterNotNull().forEach {
                val list = sourceMap.getOrPut(it.source, { mutableListOf() })
                list.add(it)
            }

            val count = AtomicInteger()

            val bookCount = if (sourceMap.isEmpty())
                0
            else
                sourceMap.map { it.value.size }.reduce { acc, i -> acc + i }
            count.lazySet(bookshelfUi.loading.max - bookCount)
            bookshelfUi.loading.secondaryProgress = count.get()

            sourceMap.forEach { entry ->
                val javaScript =  JsManager.getJs(entry.key)
                val delay = javaScript?.getScriptField<Long>(JS_FIELD_REQUEST_DELAY) ?: 1000
                if (delay < 0) {
                    entry.value.map {
                        async {
                            DataManager.reloadBookFromNet(it, javaScript)
                            bookshelfUi.loading.secondaryProgress = count.incrementAndGet()
                        }
                    }.awaitAll()
                } else {
                    entry.value.forEach {
                        DataManager.reloadBookFromNet(it, javaScript)
                        bookshelfUi.loading.secondaryProgress = count.incrementAndGet()
                        delay(delay)
                    }
                }

            }

            hideProgressBar(SHOW_FLAG_STARTING_STATION)
        }

    private var showState = 0
    private val SHOW_FLAG_REFRESH = 1
    private val SHOW_FLAG_STARTING_STATION = 2
    private suspend fun showProgressBar(flag: Int) = withMain {
        if (showState == 0) {
            bookshelfUi.loading.animFadeIn()
        }
        showState = flag or SHOW_FLAG_REFRESH
    }

    private suspend fun hideProgressBar(flag: Int) = withMain {
        showState = showState and flag.inv()
        if (showState == 0) {
            bookshelfUi.loading.animFadeOut()
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

    private class Adapter(val fragment: BookshelfFragment) : BaseViewAdapter<Book, BookListItem>() {
        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long {
            return data[position].id
        }

        override fun createView(parent: ViewGroup, viewType: Int): BookListItem {
            return BookListItem(parent.context)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: VH<BookListItem>, position: Int) {
            val book = data[position]
//            holder.setRecyclable(!book.isLoading)
            holder.itemV.apply {
                val visibleSet = constraintLayout.visibleSet()
                bookCover.glide(fragment, book.cover)
                bookName.text = book.title
                author.text = "作者：${book.author}"
                lastChapter.text = "最新：${book.lastChapter?.title}"
                haveRead.text = "已读：${book.readChapter?.title ?: "未开始阅读"}"
                loading.isLoading = book.isLoading
                val error = book.error
                val startingError = book.startingError
                if (error == null && startingError == null) {
                    visibleSet.invisible(syncError)
                    syncError.isClickable = false
                } else {
                    syncError.imageTintList = if (error != null) {
                        ColorStateList.valueOf(R.color.mdr_red_100.color(context))
                    } else {
                        ColorStateList.valueOf(R.color.mdr_grey_700.color(context))
                    }
                    visibleSet.visible(syncError)
                    syncError.setOnClickListener {
                        fragment.launch {
                            val popup = ErrorMsgPopup(fragment.context)
                                .init(
                                    "${error ?: startingError}\n" +
                                            "StackTrace:\n" +
                                            (error ?: startingError)
                                )
                                .setPopupGravity(Gravity.TOP or Gravity.START)
                            popup.showPopupWindow(it)
                        }
                    }
                }

                if (book.lastChapter?.isLastChapter == false) {
                    bvUnread.setHighlight(false)
                } else {
                    bvUnread.setHighlight(true)
                }
                if ((book.isLoading || book.unreadChapterCount <= 0) && book.lastChapter?.isLastChapter != false) {
                    visibleSet.invisible(bvUnread)
                } else {
                    visibleSet.visible(bvUnread)
                }
                bvUnread.badgeCount = book.unreadChapterCount

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

                val source = book.record?.startingStationBookSource
                if (source?.isNotBlank() == true &&
                    source != STARTING_STATION_BOOK_SOURCE_EMPTY
                ) {
                    startingStation.text = source.subSequence(0, 1)
                    visibleSet.visible(startingStation)
                } else {
                    visibleSet.invisible(startingStation)
                }

                visibleSet.apply()

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