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
    private var bookId: Int = 0
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
            startActivity<BookReaderActivity>(BOOK_ID, bookId)
        }

        bookId = arguments!!.getString(BOOK_ID)!!.toInt()
    }

    private fun refresh() {
        viewLaunch {
            DataManager.reloadBookFromNet(bookId)
            detailsRefreshLayout?.isRefreshing = false
        }
    }

    private fun initData() {

        detailsRefreshLayout.isRefreshing = true
        refresh()

        bookJobRef.get()?.cancel()
        viewLaunch {
            childFragmentManager.beginTransaction()
                .replace(R.id.chapter_list, create<ChapterListFragment>(BOOK_ID, bookId))
                .commitAllowingStateLoss()
            DataManager.getBookAndChapterListById(bookId).collectLatest {
                if (it != null) {
                    bookCover?.glide(this@BookDetailsFragment, it.cover)
                    bookName?.text = it.title
                    author?.text = it.author

                    val last = it.chapterList?.lastOrNull()
                    latestChapter?.text = last?.title
                    latestChapter.setOnClickListener {
                        startActivity<BookReaderActivity>(BOOK_ID to  bookId , CHAPTER_ID to last?.id)
                    }

                    intro?.text = it.intro
                    viewLaunch {
                        val bookList =
                            DataManager.getBookByTitleAndAuthor(it.title, it.author).firstOrNull()
                        originWebsite?.text = "来源：${it.source}共${bookList?.size}个源"
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