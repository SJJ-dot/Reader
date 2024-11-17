package com.sjianjun.reader.module.main

import android.view.*
import androidx.core.view.GravityCompat
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.*
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.databinding.MainFragmentBookDetailsBinding
import com.sjianjun.reader.module.reader.activity.BookReaderActivity
import com.sjianjun.reader.popup.ErrorMsgPopup
import com.sjianjun.reader.repository.BookSourceMgr
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull

@Suppress("EXPERIMENTAL_API_USAGE")
class BookDetailsFragment : BaseAsyncFragment() {
    private var binding: MainFragmentBookDetailsBinding? = null
    private val bookTitle: String
        get() = requireArguments().getString(BOOK_TITLE)!!

    override fun getLayoutRes() = R.layout.main_fragment_book_details

    override val onLoadedView: (View) -> Unit = {
        binding = MainFragmentBookDetailsBinding.bind(it)
        setOnBackPressed {
            if (binding?.drawerLayout?.isDrawerOpen(GravityCompat.END) == true) {
                binding?.drawerLayout?.closeDrawer(GravityCompat.END)
                true
            } else {
                false
            }
        }

        binding?.originWebsite?.setOnClickListener { _ ->
            fragmentCreate<BookSourceListFragment>(
                BOOK_TITLE to bookTitle
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
            binding?.detailsRefreshLayout?.isRefreshing = true
            DataManager.reloadBookFromNet(book)
            binding?.detailsRefreshLayout?.isRefreshing = false
        }
    }


    private fun initData() {
        childFragmentManager.beginTransaction()
            .replace(
                R.id.chapter_list,
                fragmentCreate<ChapterListFragment>(
                    BOOK_TITLE to bookTitle
                )
            )
            .commitNowAllowingStateLoss()

        launch(singleCoroutineKey = "initBookDetailsData") {
            var first = true
            DataManager.getReadingBook(bookTitle).collectLatest {
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
        binding?.bookCover?.glide(this@BookDetailsFragment, book?.cover)
        binding?.bookName?.text = book?.title
        binding?.author?.text = book?.author

        binding?.intro?.text = book?.intro.html()

        val count = DataManager.getBookBookSourceNum(bookTitle)
        val source = book?.bookSourceId?.let {
            BookSourceMgr.getBookSourceById(it).firstOrNull()
        }
        binding?.originWebsite?.text = "来源：${source?.group}-${source?.name}共${count}个源"
        val error = book?.error
        if (error == null) {
            binding?.syncError?.hide()
        } else {
            binding?.syncError?.show()
            binding?.syncError?.setOnClickListener {
                ErrorMsgPopup(context)
                    .init("$error")
                    .setPopupGravity(Gravity.BOTTOM)
                    .showPopupWindow()
            }
        }
    }

    private fun initListener(book: Book?) {
        binding?.detailsRefreshLayout?.setOnRefreshListener {
            refresh(book)
        }
        binding?.reading?.setOnClickListener {
            book ?: return@setOnClickListener
            startActivity<BookReaderActivity>(BOOK_ID, book.id)
        }
    }

    private suspend fun initLatestChapter(book: Book?) {
        DataManager.getLastChapterByBookId(book?.id ?: "")
            .collectLatest { lastChapter ->
                binding?.latestChapter?.text = lastChapter?.title
                binding?.latestChapter?.setOnClickListener { _ ->
                    book ?: return@setOnClickListener
                    startActivity<BookReaderActivity>(
                        BOOK_ID to book.id,
                        CHAPTER_INDEX to lastChapter?.index
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
                binding?.drawerLayout?.openDrawer(GravityCompat.END)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }


}