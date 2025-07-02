package com.sjianjun.reader.module.main

import android.annotation.SuppressLint
import android.view.*
import androidx.core.view.GravityCompat
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.*
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.databinding.MainFragmentBookDetailsBinding
import com.sjianjun.reader.module.reader.activity.BookReaderActivity
import com.sjianjun.reader.popup.ErrorMsgPopup
import com.sjianjun.reader.repository.BookSourceMgr
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import com.sjianjun.reader.view.click
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
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

        binding?.originWebsite?.click { _ ->
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
            DataManager.getReadingBook(bookTitle).collectLatest {
                fillView(it)
                initListener(it)
                initLatestChapter(it)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private suspend fun fillView(book: Book?) {
        binding?.bookCover?.glide(book?.cover)
        binding?.bookName?.text = book?.title
        binding?.author?.text = "作者：${book?.author}"

        binding?.intro?.text = book?.intro.format(true)

        val count = DataManager.getBookBookSourceNum(bookTitle)
        val source = book?.bookSourceId?.let {
            BookSourceMgr.getBookSourceById(it).firstOrNull()
        }
        binding?.originWebsite?.text = "${source?.group}：${source?.name}共${count}个"
        val error = book?.error
        if (error == null) {
            binding?.syncError?.hide()
        } else {
            binding?.syncError?.show()
            binding?.syncError?.click {
                ErrorMsgPopup(context)
                    .init(error)
                    .setPopupGravity(Gravity.BOTTOM)
                    .showPopupWindow()
            }
        }
    }

    private fun initListener(book: Book?) {
        binding?.detailsRefreshLayout?.setOnRefreshListener {
            refresh(book)
        }
        binding?.reading?.click {
            book ?: return@click
            startActivity<BookReaderActivity>(BOOK_ID, book.id)
        }
    }

    @SuppressLint("SetTextI18n")
    private suspend fun initLatestChapter(book: Book?) {
        DataManager.getLastChapterByBookId(book?.id ?: "")
            .collectLatest { lastChapter ->
                binding?.latestChapter?.text = "最新：${lastChapter?.title ?: "无"}"
                binding?.latestChapter?.click { _ ->
                    book ?: return@click
                    launch {
                        val readingRecord = DataManager.getReadingRecord(book).first() ?: ReadingRecord(book.title, book.id)
                        if (readingRecord.chapterIndex != lastChapter?.index) {
                            readingRecord.chapterIndex = lastChapter?.index ?: 0
                            readingRecord.offest = 0
                            readingRecord.isEnd = false
                            readingRecord.updateTime = System.currentTimeMillis()
                            DataManager.setReadingRecord(readingRecord)
                        }
                        startActivity<BookReaderActivity>(BOOK_ID to book.id)
                    }


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