package com.sjianjun.reader.module.bookcity

import android.os.Bundle
import android.view.View
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager.HORIZONTAL
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Page
import com.sjianjun.reader.repository.DataManager.pageDataStore
import com.sjianjun.reader.utils.*
import kotlinx.android.synthetic.main.bookcity_fragment_page.*
import kotlinx.android.synthetic.main.bookcity_item_book_group.view.*
import kotlinx.android.synthetic.main.bookcity_item_book_group_title.view.*
import kotlinx.android.synthetic.main.bookcity_item_page_title.view.*
import kotlin.math.min

class BookCityPageFragment : BaseFragment() {
    private lateinit var pageListLayoutManager: StaggeredGridLayoutManager
    private lateinit var pageListAdapter: PageListAdapter
    private lateinit var bookListAdapter: BookListAdapter

    private val pageId by lazy { arguments?.getString(PAGE_ID) ?: "" }

    override fun getLayoutRes(): Int {
        return R.layout.bookcity_fragment_page
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pageListLayoutManager = StaggeredGridLayoutManager(1, HORIZONTAL)
        recycle_view_page_list.layoutManager = pageListLayoutManager
        pageListAdapter = PageListAdapter()
        recycle_view_page_list.adapter = pageListAdapter

        bookListAdapter = BookListAdapter(this)
        recycle_view.layoutManager = LinearLayoutManager(context)
        recycle_view.adapter = bookListAdapter

        initData()
    }


    private fun initData() {
        pageDataStore[pageId]?.let {
            it.pageList?.let { pageList ->
                pageListLayoutManager.spanCount = min(pageList.size / 2, 2)
                pageListAdapter.pageList = pageList
                pageListAdapter.notifyDataSetChanged()
            }
            it.bookGroupList?.let { bookGroupList ->
                bookListAdapter.data = bookGroupList
                bookListAdapter.notifyDataSetChanged()
            }
        }
    }


    class PageListAdapter : BaseAdapter(R.layout.bookcity_item_page_title) {
        var pageList: List<Page> = emptyList()
        override fun getItemCount(): Int = pageList.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val page = pageList[position]
            holder.itemView.apply {
                page_title.text = page.title
                setOnClickListener {
                    pageDataStore[page.pageId] = page
                    findNavController().navigate(
                        R.id.bookCityFragment, bundle(PAGE_ID to page.pageId)
                    )
                }
            }
        }
    }

    class BookListAdapter(val fragment: BookCityPageFragment) : BaseAdapter() {
        val ITEM_TYPE_TITLE = 1
        val ITEM_TYPE_BOOK = 2

        var data: List<Page.BookGroup> = emptyList()
            set(value) {
                field = value
                val dataList = mutableListOf<Any>()
                data.forEach {
                    dataList.add(it)
                    it.bookList?.let { list ->
                        dataList.addAll(list)
                    }
                }
                viewData = dataList
            }
        var viewData: List<Any> = emptyList()
            private set


        override fun getItemViewType(position: Int): Int {
            return if (viewData[position] is Page.BookGroup) ITEM_TYPE_TITLE else ITEM_TYPE_BOOK
        }

        override fun itemLayoutRes(viewType: Int): Int {
            return if (viewType == ITEM_TYPE_TITLE)
                R.layout.bookcity_item_book_group_title
            else
                R.layout.bookcity_item_book_group
        }

        override fun getItemCount(): Int = viewData.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            holder.itemView.apply {
                if (getItemViewType(position) == ITEM_TYPE_TITLE) {
                    book_group_title.text = (viewData[position] as Page.BookGroup).title
                } else {
                    val book = viewData[position] as Book
                    bookCover.glide(fragment, book.cover)
                    bookName.text = book.title
                    author.text = book.author
                    intro.text = book.intro
                    setOnClickListener {
                        findNavController().navigate(
                            R.id.searchFragment,
                            bundle(SEARCH_KEY, book.title)
                        )
                    }
                }
            }
        }
    }

}