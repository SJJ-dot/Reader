package com.sjianjun.reader.module.main.fragment

import android.content.Context
import android.os.Bundle
import android.util.SparseArray
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.preferences.LiveDataMap
import com.sjianjun.reader.preferences.LiveDataMapImpl
import com.sjianjun.reader.preferences.globalBookConfig
import com.sjianjun.reader.preferences.liveDataMap
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import kotlinx.android.synthetic.main.item_book_list.view.*
import kotlinx.android.synthetic.main.main_fragment_book_shelf.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import sjj.alog.Log
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicReference

class BookshelfFragment : BaseFragment() {

    private val bookList = MutableLiveData<List<Book>>()
    private lateinit var adapter: Adapter
    override fun getLayoutRes() = R.layout.main_fragment_book_shelf
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        adapter = Adapter(this)
        recycle_view.adapter = adapter

        bookList.observeViewLifecycle {
            adapter.data.clear()
            adapter.data.addAll(it)
            adapter.notifyDataSetChanged()
        }

        swipe_refresh.setOnRefreshListener {
            launch {
                bookList.value?.forEach {
                    DataManager.reloadBookFromNet(it.id)
                }
                swipe_refresh.isRefreshing = false
            }
        }

        val mItemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                return makeMovementFlags(0, ItemTouchHelper.LEFT)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false
//            val removeAt = adapter.data.removeAt(viewHolder.adapterPosition)
//            adapter.data.add(target.adapterPosition,removeAt)
//            adapter.notifyItemMoved(viewHolder.adapterPosition,target.adapterPosition)
//            return true

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                launch {
                    val book = adapter.data.getOrNull(viewHolder.adapterPosition)
                    DataManager.deleteBook(book ?: return@launch)
                }
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
//                if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
//                    val data = adapter.data.map { it.bookSourceRecord }
//                    data.forEachIndexed { index, bookSourceRecord ->
//                        bookSourceRecord.sequence = index
//                    }
//                    model.updateBookSourceRecordSeq(data)
//                        .subscribe()
//                        .destroy("fragment Book shelf update bookSourceRecord sequence")
//                }
            }
        })
        mItemTouchHelper.attachToRecyclerView(recycle_view)

        launch {
            //需要最新章节 阅读章节 书源数量
            //这里用视图做更方便一些
            val book = AtomicReference<Job>()
            DataManager.getAllReadingBook().collectLatest {

                book.get()?.cancel()
                launch {

                    it.forEach { book ->
                        //查询 阅读章节
                        launch {
                            DataManager.getReadingRecord(book)
                                .mapLatest { record ->
                                    DataManager.getChapterById(record.id).firstOrNull()
                                }.collectLatest { readChapter ->
                                    book.readChapter = readChapter
                                    bookList.postValue(it)
                                }
                        }
                        //最新章节
                        launch {
                            DataManager.getLastChapterByBookId(book.id)
                                .collectLatest { lastChapter ->
                                    book.lastChapter = lastChapter
                                    bookList.postValue(it)
                                }
                        }
                        //查询书源
                        launch {
                            DataManager.getHasBookJavaScript().collectLatest { list ->
                                book.javaScriptList = list
                                bookList.postValue(it)
                            }
                        }
                    }
                }.apply(book::lazySet)

                bookList.postValue(it)

            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main_fragment_book_shelf_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
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

    private class Adapter(val fragment: BookshelfFragment) : BaseAdapter() {
        init {
            setHasStableIds(true)
        }

        val data = mutableListOf<Book>()

        override fun itemLayoutRes(viewType: Int) = R.layout.item_book_list

        override fun getItemCount(): Int = data.size

        override fun getItemId(position: Int): Long {
            return data[position].id.toLong()
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val book = data[position]
            holder.itemView.bookCover.glide(fragment, book.cover)
            holder.itemView.bookName.text = book.title
            holder.itemView.author.text = "作者：${book.author}"
            holder.itemView.lastChapter.text = "最新：${book.lastChapter?.title}"
            holder.itemView.haveRead.text = "已读：${book.readChapter?.title ?: "未开始阅读"}"
            holder.itemView.origin.text = "来源：${book.source}共${book.javaScriptList?.size}个源"

            val l = View.OnClickListener {
                fragment.findNavController()
                    .navigate(R.id.bookDetailsFragment, bundle(BOOK_ID, book.id))
            }
            holder.itemView.intro.setOnClickListener(l)
            holder.itemView.bookCover.setOnClickListener(l)

            holder.itemView.setOnClickListener {
                fragment.findNavController().navigate(R.id.bookReaderFragment)
            }
        }
    }
}