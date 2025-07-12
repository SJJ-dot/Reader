package com.sjianjun.reader.module.main

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.GravityCompat
import androidx.fragment.app.viewModels
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BOOK_ID
import com.sjianjun.reader.BOOK_TITLE
import com.sjianjun.reader.BaseAsyncFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.databinding.MainFragmentBookDetailsBinding
import com.sjianjun.reader.module.reader.activity.BookReaderActivity
import com.sjianjun.reader.module.reader.activity.BrowserReaderActivity
import com.sjianjun.reader.popup.ErrorMsgPopup
import com.sjianjun.reader.utils.dp2Px
import com.sjianjun.reader.utils.format
import com.sjianjun.reader.utils.fragmentCreate
import com.sjianjun.reader.utils.glide
import com.sjianjun.reader.utils.hide
import com.sjianjun.reader.utils.show
import com.sjianjun.reader.utils.startActivity
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.click

@Suppress("EXPERIMENTAL_API_USAGE")
class BookDetailsFragment : BaseAsyncFragment() {
    private var binding: MainFragmentBookDetailsBinding? = null
    private val bookTitle: String
        get() = requireArguments().getString(BOOK_TITLE)!!
    private val viewModel: BookDetailsViewModel by viewModels()

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

        binding?.originClickableArea?.click { _ ->
            fragmentCreate<BookSourceListFragment>(
                BOOK_TITLE to bookTitle
            ).show(childFragmentManager, "BookSourceListFragment")
        }
        setHasOptionsMenu(true)
        init()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        setHasOptionsMenu(false)
    }

    private fun init() {
        childFragmentManager.beginTransaction()
            .replace(
                R.id.chapter_list,
                fragmentCreate<ChapterListFragment>(
                    BOOK_TITLE to bookTitle
                )
            )
            .commitNowAllowingStateLoss()
        viewModel.init(bookTitle)
        viewModel.bookLivedata.observeViewLifecycle {
            fillView(it)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun fillView(book: Book?) {
        binding?.bookCover?.glide(book?.cover)
        binding?.bookName?.text = book?.title
        binding?.author?.text = "作者：${book?.author}"
        val intro = book?.intro.format(true)
        binding?.intro?.text = intro.ifBlank { "暂无简介" }
        binding?.bookClickableArea?.click {
            //使用浏览器打开书籍链接
            val url = book?.readChapter?.url
            if (url != null) {
                BrowserReaderActivity.startActivity(requireActivity(), url)
                return@click
            }
            val bookUrl = book?.url
            if (bookUrl.isNullOrBlank()) {
                toast("书籍链接为空")
                return@click
            }
            BrowserReaderActivity.startActivity(requireActivity(), bookUrl)
        }

        binding?.originWebsite?.text = "${book?.bookSource?.group}：${book?.bookSource?.name}共${book?.bookSourceCount}个"
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
        binding?.readingChapter?.text = "已读：${book?.readChapter?.title ?: "无"}"

        binding?.originWebsite?.post {
            binding?.bookName?.maxWidth = (binding?.bookClickableArea?.measuredWidth ?: 0) - 30.dp2Px
            binding?.originWebsite?.maxWidth = (binding?.originClickableArea?.measuredWidth ?: 0) - 25.dp2Px
        }
        binding?.detailsRefreshLayout?.isRefreshing = book?.isLoading == true
        binding?.detailsRefreshLayout?.setOnRefreshListener {
            viewModel.reloadBookFromNet()
        }
        binding?.reading?.click {
            book ?: return@click
            startActivity<BookReaderActivity>(BOOK_ID, book.id)
        }
        val lastChapter = book?.lastChapter
        binding?.latestChapter?.text = "最新：${lastChapter?.title ?: "无"}"
        binding?.latestChapter?.click { _ ->
            book ?: return@click
            launch {
                viewModel.setRecordToLastChapter()
                startActivity<BookReaderActivity>(BOOK_ID to book.id)
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