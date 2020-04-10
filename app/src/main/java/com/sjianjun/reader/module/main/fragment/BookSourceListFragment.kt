package com.sjianjun.reader.module.main.fragment

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import com.sjianjun.reader.view.isLoading
import kotlinx.android.synthetic.main.main_fragment_book_source_list.*
import kotlinx.android.synthetic.main.main_item_fragment_book_source_list.view.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*


class BookSourceListFragment : BaseFragment() {

    private val bookTitle by lazy { arguments!!.getString(BOOK_TITLE)!! }
    private val bookAuthor by lazy { arguments!!.getString(BOOK_AUTHOR)!! }


    private val adapter by lazy { BookListAdapter(this) }
    override fun getLayoutRes() = R.layout.main_fragment_book_source_list

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycle_view.adapter = adapter
        viewLaunch {
            DataManager.getBookByTitleAndAuthor(bookTitle, bookAuthor)
                .map {
                    //并发读取章节列表
                    it.asFlow().flatMapMerge { book ->
                        flow {
                            book.lastChapter = DataManager.getLastChapterByBookUrl(book.url).first()
                            emit(book)
                        }
                    }.toList()
                }.collectLatest {
                    adapter.data = it
                    adapter.notifyDataSetChanged()
                }
        }

        initRefresh()
    }

    private fun initRefresh() {

        swipe_refresh.setOnRefreshListener {
            viewLaunch {
                swipe_refresh.isRefreshing = false
                swipe_refresh.isEnabled = false
                refresh_progress_bar.isAutoLoading = true

                adapter.data.map {
                    async { DataManager.reloadBookFromNet(it.url) }
                }.awaitAll()

                refresh_progress_bar.isAutoLoading = false
                swipe_refresh.isEnabled = true
            }
        }
    }

    class BookListAdapter(val fragment: BookSourceListFragment) : BaseAdapter() {
        init {
            setHasStableIds(true)
        }

        var data: List<Book> = emptyList()
        override fun getItemCount() = data.size

        override fun itemLayoutRes(viewType: Int) = R.layout.main_item_fragment_book_source_list

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val book = data[position]
            holder.itemView.apply {
                bookCover.glide(fragment, book.cover)
                bookName.text = book.title
                author.text = "作者：${book.author}"
                lastChapter.text = "最新：${book.lastChapter?.title}"
                haveRead.text = "来源：${book.source}"
                loading.isLoading = book.isLoading
                setOnClickListener {
                    fragment.viewLaunch {
                        val readingRecord = DataManager.getReadingRecord(book).first()!!
                        readingRecord.bookUrl = book.url
                        val chapter = DataManager.getChapterByUrl(readingRecord.chapterUrl).first()
                        var readChapter: Chapter? = null
                        if (chapter != null) {
                            readChapter = DataManager.getChapterByTitle(chapter.title!!).first()
                            if (readChapter == null) {
                                readChapter = DataManager.getChapterByIndex(chapter.index).first()
                            }
                        }
                        readingRecord.chapterUrl = readChapter?.url ?: ""
                        DataManager.setReadingRecord(readingRecord)
                        fragment.dismissAllowingStateLoss()
                    }

                }
            }
        }

        override fun getItemId(position: Int): Long {
            return data[position].url.id
        }
    }

}
