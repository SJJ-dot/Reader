package com.sjianjun.reader.module.main

import android.view.*
import androidx.core.view.GravityCompat
import androidx.navigation.fragment.findNavController
import com.sjianjun.reader.BaseAsyncFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.coroutine.launch
import com.sjianjun.reader.module.reader.activity.BookReaderActivity
import com.sjianjun.reader.popup.ErrorMsgPopup
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import kotlinx.android.synthetic.main.main_fragment_book_details.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull

@Suppress("EXPERIMENTAL_API_USAGE")
class BookDetailsFragment : BaseAsyncFragment() {
    private val bookTitle: String
        get() = requireArguments().getString(BOOK_TITLE)!!

    private val bookAuthor: String
        get() = requireArguments().getString(BOOK_AUTHOR)!!

    override fun getLayoutRes() = R.layout.main_fragment_book_details

    override val onLoadedView: (View) -> Unit = {

        setOnBackPressed {
            if (drawer_layout?.isDrawerOpen(GravityCompat.END) == true) {
                drawer_layout?.closeDrawer(GravityCompat.END)
                true
            } else {
                false
            }
        }

        originWebsite.setOnClickListener { _ ->
            fragmentCreate<BookSourceListFragment>(
                BOOK_TITLE to bookTitle,
                BOOK_AUTHOR to bookAuthor
            ).show(childFragmentManager, "BookSourceListFragment")
        }
        setHasOptionsMenu(true)
        initData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        setHasOptionsMenu(false)
    }

    private fun refresh(book: Book?) {
        book ?: return
        launch(singleCoroutineKey = "refreshBookDetails") {
            detailsRefreshLayout?.isRefreshing = true
            val qiDian = async {
                val startingBook = DataManager.getStartingBook(book)
                if (startingBook?.source != book.source) {
                    DataManager.reloadBookFromNet(startingBook)
                }
            }
            DataManager.reloadBookFromNet(book)
            qiDian.await()
            detailsRefreshLayout?.isRefreshing = false
        }
    }


    private fun initData() {
        childFragmentManager.beginTransaction()
            .replace(
                R.id.chapter_list,
                fragmentCreate<ChapterListFragment>(
                    BOOK_TITLE to bookTitle,
                    BOOK_AUTHOR to bookAuthor
                )
            )
            .commitNowAllowingStateLoss()

        launch(singleCoroutineKey = "initBookDetailsData") {
            var first = true
            DataManager.getReadingBook(bookTitle, bookAuthor).collectLatest {
                if (it != null) {
                    val startingBook = DataManager.getStartingBook(it, onlyLocal = true)
                    it.startingError = startingBook?.error
                }

                fillView(it)

                initListener(it)

                if (first) {
                    first = false
                    refresh(it)
                }
                initLatestChapter(it)
            }
        }
    }

    private suspend fun fillView(book: Book?) {
        bookCover?.glide(this@BookDetailsFragment, book?.cover)
        bookName?.text = book?.title
        author?.text = book?.author

        intro?.text = book?.intro.html()

        val bookList = DataManager.getBookByTitleAndAuthor(bookTitle, bookAuthor).firstOrNull()
        originWebsite?.text = "来源：${book?.source}共${bookList?.size}个源"
        val error = book?.error ?: book?.startingError
        if (error == null) {
            sync_error.hide()
        } else {
            sync_error.show()
            sync_error.setOnClickListener {
                ErrorMsgPopup(context)
                    .init(
                        "${error}\n" +
                                "StackTrace:\n" +
                                error
                    )
                    .setPopupGravity(Gravity.BOTTOM or Gravity.START)
                    .showPopupWindow(it)
            }
        }
    }

    private fun initListener(book: Book?) {
        detailsRefreshLayout.setOnRefreshListener {
            refresh(book)
        }
        reading.setOnClickListener {
            book ?: return@setOnClickListener
            startActivity<BookReaderActivity>(BOOK_URL, book.url)
        }
    }

    private suspend fun initLatestChapter(book: Book?) {
        DataManager.getLastChapterByBookUrl(book?.url ?: "")
            .collectLatest { lastChapter ->
                latestChapter?.text = lastChapter?.title
                if (lastChapter?.isLastChapter == false) {
                    red_dot.show()
                } else {
                    red_dot.hide()
                }
                latestChapter.setOnClickListener { _ ->
                    book ?: return@setOnClickListener
                    startActivity<BookReaderActivity>(
                        BOOK_URL to book.url,
                        CHAPTER_URL to lastChapter?.url
                    )
                }
            }

    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main_fragment_book_details_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.chapter_list -> {
                drawer_layout.openDrawer(GravityCompat.END)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


}