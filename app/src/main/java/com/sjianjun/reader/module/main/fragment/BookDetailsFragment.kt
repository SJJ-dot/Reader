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
import com.sjianjun.reader.module.reader.activity.BookReaderActivity
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import kotlinx.android.synthetic.main.main_fragment_book_details.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.atomic.AtomicReference

class BookDetailsFragment : BaseFragment() {
    private var bookUrl: String = ""
        set(value) {
            field = value
            initData()
        }
    private val bookJobRef = AtomicReference<Job>()

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

        detailsRefreshLayout.setOnRefreshListener {
            refresh()
        }

        reading.setOnClickListener {
            startActivity<BookReaderActivity>(BOOK_URL, bookUrl)
        }

        bookUrl = arguments!!.getString(BOOK_URL)!!
    }

    private fun refresh() {
        viewLaunch {
            DataManager.reloadBookFromNet(bookUrl)
            detailsRefreshLayout?.isRefreshing = false
        }
    }

    private fun initData() {

        detailsRefreshLayout.isRefreshing = true
        refresh()

        bookJobRef.get()?.cancel()
        viewLaunch {
            childFragmentManager.beginTransaction()
                .replace(R.id.chapter_list, fragmentCreate<ChapterListFragment>(BOOK_URL, bookUrl))
                .commitAllowingStateLoss()
            DataManager.getBookAndChapterList(bookUrl).collectLatest {
                if (it != null) {
                    bookCover?.glide(this@BookDetailsFragment, it.cover)
                    bookName?.text = it.title
                    author?.text = it.author

                    val last = it.chapterList?.lastOrNull()
                    latestChapter?.text = last?.title
                    latestChapter.setOnClickListener {
                        startActivity<BookReaderActivity>(
                            BOOK_URL to bookUrl,
                            CHAPTER_URL to last?.url
                        )
                    }

                    intro?.text = it.intro

                    val bookList = DataManager.getBookByTitleAndAuthor(it.title, it.author)
                        .firstOrNull()
                    originWebsite?.text = "来源：${it.source}共${bookList?.size}个源"
                    originWebsite.setOnClickListener {_->
                        fragmentCreate<BookSourceListFragment>(
                            BOOK_TITLE to it.title,
                            BOOK_AUTHOR to it.author
                        ).show(childFragmentManager, "BookSourceListFragment")
                    }

                    detailsRefreshLayout?.isRefreshing = false
                }
            }
        }.apply(bookJobRef::lazySet)
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