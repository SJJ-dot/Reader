package com.sjianjun.reader.module.main.fragment

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.GravityCompat
import androidx.navigation.fragment.findNavController
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.module.reader.activity.BookReaderActivity
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import kotlinx.android.synthetic.main.main_fragment_book_details.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

class BookDetailsFragment : BaseFragment() {
    private val bookTitle: String
        get() = arguments!!.getString(BOOK_TITLE)!!

    private val bookAuthor: String
        get() = arguments!!.getString(BOOK_AUTHOR)!!

    override fun getLayoutRes() = R.layout.main_fragment_book_details
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)

        onBackPressed = {
            if (drawer_layout?.isDrawerOpen(GravityCompat.END) == true) {
                drawer_layout?.closeDrawer(GravityCompat.END)
            } else {
                findNavController().popBackStack()
            }
        }

        originWebsite.setOnClickListener { _ ->
            fragmentCreate<BookSourceListFragment>(
                BOOK_TITLE to bookTitle,
                BOOK_AUTHOR to bookAuthor
            ).show(childFragmentManager, "BookSourceListFragment")
        }

        initData()
    }

    private fun refresh(bookUrl: String?) {
        bookUrl ?: return
        viewLaunch {
            detailsRefreshLayout?.isRefreshing = true
            DataManager.reloadBookFromNet(bookUrl)
            detailsRefreshLayout?.isRefreshing = false
        }
    }

    private fun initData() {


        viewLaunch {
            var first = true
            DataManager.getReadingBook(bookTitle, bookAuthor).collectLatest {
                childFragmentManager.beginTransaction()
                    .replace(
                        R.id.chapter_list,
                        fragmentCreate<ChapterListFragment>(BOOK_URL, it?.url ?: "")
                    )
                    .commitNowAllowingStateLoss()

                fillView(it)

                initListener(it)

                initLatestChapter(it)
                if (first) {
                    first = false
                    refresh(it?.url)
                }

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
    }

    private fun initListener(book: Book?) {
        detailsRefreshLayout.setOnRefreshListener {
            refresh(book?.url)
        }
        reading.setOnClickListener {
            book ?: return@setOnClickListener
            startActivity<BookReaderActivity>(BOOK_URL, book.url)
        }
    }

    private val latestChapterJob = AtomicReference<Job>()
    private fun initLatestChapter(book: Book?) {
        latestChapterJob.get()?.cancel()
        launch {
            DataManager.getLastChapterByBookUrl(book?.url ?: "")
                .collectLatest { lastChapter ->
                    latestChapter?.text = lastChapter?.title
                    latestChapter.setOnClickListener { _ ->
                        book ?: return@setOnClickListener
                        startActivity<BookReaderActivity>(
                            BOOK_URL to book.url,
                            CHAPTER_URL to lastChapter?.url
                        )
                    }
                }
        }.apply(latestChapterJob::lazySet)

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