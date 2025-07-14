package com.sjianjun.reader.module.shelf

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BOOK_ID
import com.sjianjun.reader.BOOK_TITLE
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.databinding.ItemBookListBinding
import com.sjianjun.reader.databinding.MainFragmentBookShelfBinding
import com.sjianjun.reader.module.main.BookSourceListFragment
import com.sjianjun.reader.module.reader.activity.BookReaderActivity
import com.sjianjun.reader.popup.ErrorMsgPopup
import com.sjianjun.reader.utils.bundle
import com.sjianjun.reader.utils.color
import com.sjianjun.reader.utils.dp2Px
import com.sjianjun.reader.utils.fragmentCreate
import com.sjianjun.reader.utils.glide
import com.sjianjun.reader.utils.id
import com.sjianjun.reader.utils.key
import com.sjianjun.reader.utils.startActivity
import com.sjianjun.reader.utils.visibleSet
import com.sjianjun.reader.view.click
import com.sjianjun.reader.view.clickWithDouble
import com.sjianjun.reader.view.isLoading
import kotlinx.coroutines.FlowPreview
import java.util.concurrent.ConcurrentHashMap

@FlowPreview
class BookshelfFragment : BaseFragment() {
    private val bookList = ConcurrentHashMap<String, Book>()
    private lateinit var adapter: Adapter
    private lateinit var bookShelfBinding: MainFragmentBookShelfBinding
    private val bookShelfTitle = BookShelfTitle()
    private var welcomeDialog: Boolean = true
    private val viewModel by viewModels<BookShelfViewModel>()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        bookShelfBinding = MainFragmentBookShelfBinding.inflate(inflater, container, false)
        return bookShelfBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        initView()
        initData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        setHasOptionsMenu(false)
        bookShelfTitle.destroyTileView()
    }

    private fun initView() {
        adapter = Adapter(this@BookshelfFragment)
        bookShelfBinding.recycleView.adapter = adapter
        bookShelfBinding.rootRefresh.setOnRefreshListener {
            launch {
                viewModel.reloadBookFromNet()
                bookShelfBinding.rootRefresh.isRefreshing = false
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun welcome() {
        lifecycle.launch {
            MaterialAlertDialogBuilder(requireActivity())
                .setTitle("欢迎使用")
                .setMessage("首次使用APP,你可以：\n1、从左侧菜单进入书城选择一本书。\n2、还是从左侧菜单进入搜索页，搜索你想看的书籍。\n3、点击搜索结果即可开始阅读，书籍会被自动加入书架\n4、为防阅读记录丢失建议配置WebDav保存阅读记录")
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun initData() {
        viewModel.init()
        viewModel.bookList.observeViewLifecycle {
            adapter.data.clear()
            adapter.data.addAll(it)
            adapter.notifyDataSetChanged()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main_fragment_book_shelf_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
        bookShelfTitle.initTileView(this)
        bookShelfTitle.showSourceData(true)
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

    private class Adapter(val fragment: BookshelfFragment) : BaseAdapter<Book>(R.layout.item_book_list) {
        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long {
            return data[position].id.id
        }


        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

            val book = data[position]
            val binding = ItemBookListBinding.bind(holder.itemView)
            binding.apply {
                val visibleSet = constraintLayout.visibleSet()
                bookCover.glide(book.cover)
                bookName.text = book.title
                if (author.tag == null) {
                    author.post {
                        author.maxWidth = bookName.measuredWidth - 85.dp2Px
                        author.tag = author
                    }
                }
                author.text = "作者：${book.author}"
                lastChapter.text = "最新：${book.lastChapter?.title}"
                haveRead.text = "已读：${book.readChapter?.title ?: "未开始阅读"}"
                loading.isLoading = book.isLoading
                val error = book.error
                if (error == null) {
                    visibleSet.invisible(syncError)
                    syncError.isClickable = false
                } else {
                    syncError.imageTintList = ColorStateList.valueOf(R.color.mdr_red_100.color(root.context))
                    visibleSet.visible(syncError)
                    syncError.click {
                        fragment.launch {
                            val popup = ErrorMsgPopup(fragment.context)
                                .init("$error")
                                .setPopupGravity(Gravity.BOTTOM)
                            popup.showPopupWindow()
                        }
                    }
                }

                if (book.readChapter?.content?.firstOrNull()?.contentError == true) {
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
                if (origin.tag == null) {
                    origin.post {
                        origin.maxWidth = bookName.measuredWidth - 20.dp2Px
                        origin.tag = origin
                    }
                }
                origin.text =
                    "${book.bookSource?.group}：${book.bookSource?.name}共${book.javaScriptList?.size}个"
                originClickableArea.click {
                    fragmentCreate<BookSourceListFragment>(
                        BOOK_TITLE to book.title
                    ).show(fragment.childFragmentManager, "BookSourceListFragment")
                }

                detailClickableArea.click {
                    fragment.findNavController()
                        .navigate(
                            R.id.bookDetailsFragment,
                            bundle(BOOK_TITLE to book.title)
                        )
                }

                root.clickWithDouble(onClick = {
                    fragment.startActivity<BookReaderActivity>(BOOK_ID, book.id)
                }, onDoubleClick = {
                    if (!book.isLoading) {
                        fragment.viewModel.reloadBookFromNet(book)
                    }
                })

                visibleSet.apply()

                root.setOnLongClickListener {
                    AlertDialog.Builder(root.context)
                        .setTitle("确认删除")
                        .setMessage("确定要删除《${book.title}》吗?")
                        .setPositiveButton("删除") { _, _ ->
                            fragment.bookList.remove(book.key)
                            data.removeAt(position)
                            notifyItemRemoved(position)
                            fragment.viewModel.deleteBook(book)
                        }
                        .show()
                    true
                }
            }

        }
    }
}