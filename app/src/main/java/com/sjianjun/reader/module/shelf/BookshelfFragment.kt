package com.sjianjun.reader.module.shelf

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import com.sjianjun.reader.repository.BookSourceManager
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import com.sjianjun.reader.view.isLoading
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

private var needShowWelcome = true

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

    private fun welcome() {
        if (!needShowWelcome) {
            return
        }
        needShowWelcome = false
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle("欢迎使用本APP")
            .setMessage("首次使用APP,你可以：\n1、从左侧菜单进入书城选择一本书。\n2、还是从左侧菜单进入搜索页，搜索你想看的书籍。\n3、点击搜索结果即可开始阅读，书籍会被自动加入书架\n4、为防阅读记录丢失建议配置WebDav保存阅读记录")
            .setPositiveButton(android.R.string.ok, null)
            .show()
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
                        DataManager.getReadingRecord(book),
                        DataManager.getLastChapterByBookId(book.id)
                    ) { record, lastChapter ->
                        book.record = record
                        val chapter = DataManager.getChapterByIndex(
                            record?.bookId ?: "",
                            record?.chapterIndex ?: -1
                        ).firstOrNull()
                        book.readChapter = chapter
                        if (chapter != null) {
                            val content = DataManager.getChapterContent(chapter, -1)
                            book.readingContentError = content.content?.contentError
                        }

                        book.lastChapter = lastChapter
                        val js = BookSourceManager.getBookBookSource(book.title, book.author)
                        book.javaScriptList = js
                        book.bookSource =
                            BookSourceManager.getBookSourceById(book.bookSourceId).firstOrNull()
                        val lastChapterIndex = book.lastChapter?.index ?: 0
                        val readChapterIndex = book.readChapter?.index ?: 0
                        book.unreadChapterCount = if (book.record?.isEnd == true) {
                            lastChapterIndex - readChapterIndex
                        } else {
                            lastChapterIndex - readChapterIndex + 1
                        }

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
                    if (list.isEmpty()) {
                        welcome()
                    } else {
                        needShowWelcome = false
                    }
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
                    val list = sourceMap.getOrPut(it.bookSourceId) { mutableListOf() }
                    list.add(it)
                }

                showProgressBar(SHOW_FLAG_REFRESH)
                bookshelfUi.loading.progress = 0
                sourceMap.map {
                    async {
                        val script = it.value.firstOrNull()?.javaScriptList?.find { js ->
                            js.id == it.key
                        }
                        val delay = script?.requestDelay ?: 1000L
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
            return data[position].id.id
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
                if (error == null) {
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
                                    "${error}"
                                )
                                .setPopupGravity(Gravity.TOP or Gravity.START)
                            popup.showPopupWindow(it)
                        }
                    }
                }
                if (book.readingContentError == true) {
                    bvUnread.setHighlight(false)
                } else {
                    bvUnread.setHighlight(true)
                }
                if ((book.isLoading || book.unreadChapterCount <= 0)) {
                    visibleSet.invisible(bvUnread)
                } else {
                    visibleSet.visible(bvUnread)
                }
                bvUnread.badgeCount = book.unreadChapterCount

                origin.text =
                    "来源：${book.bookSource?.group}-${book.bookSource?.name}共${book.javaScriptList?.size}个源"
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
                    fragment.startActivity<BookReaderActivity>(BOOK_ID, book.id)
                }

                visibleSet.invisible(startingStation)

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