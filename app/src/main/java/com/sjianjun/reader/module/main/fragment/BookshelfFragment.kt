package com.sjianjun.reader.module.main.fragment

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.Observer
import androidx.navigation.fragment.NavHostFragment
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.preferences.LiveDataMap
import com.sjianjun.reader.preferences.LiveDataMapImpl
import com.sjianjun.reader.preferences.liveDataMap
import com.sjianjun.reader.repository.DataManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sjj.alog.Log
import java.lang.reflect.Modifier

class BookshelfFragment : BaseFragment() {
    override fun getLayoutRes() = R.layout.main_fragment_book_shelf
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        launch {
            DataManager.getAllBook().toLiveData().observeViewLifecycle(Observer {
                Log.e(it)
            })
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

//    private inner class Adapter : BaseAdapter() {
//        val data = mutableListOf<Book>()
//
//        override fun itemLayoutRes(viewType: Int): Int {
//            return R.layout.item_book_list
//        }
//
//        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
//            val bind = DataBindingUtil.bind<ItemBookListBinding>(holder.itemView)
//            val viewModel = data.get(position)
//            bind!!.model = viewModel
//            viewModel.bookCover.onBackpressureLatest().observeOnMain().subscribe {
//                Glide.with(this@BookshelfFragment)
//                    .applyDefaultRequestOptions(requestOptions)
//                    .load(it)
//                    .into(holder.itemView.bookCover)
//            }.destroy("fragment shelf book cover ${viewModel.id}")
//
//            holder.itemView.setOnClickListener { v ->
//                startActivity<ReadActivity>(
//                    ReadActivity.BOOK_NAME to viewModel.book.name,
//                    ReadActivity.BOOK_AUTHOR to viewModel.book.author
//                )
//            }
//            holder.itemView.intro.setOnClickListener {
//                startActivity<DetailsActivity>(
//                    DetailsActivity.BOOK_NAME to viewModel.book.name,
//                    DetailsActivity.BOOK_AUTHOR to viewModel.book.author
//                )
//            }
//            holder.itemView.origin.setOnClickListener {
//                ChooseBookSourceFragment.newInstance(viewModel.book.name, viewModel.book.author)
//                    .show(fragmentManager!!)
//            }
//        }
//
//        override fun getItemCount(): Int = data.size
//
//        override fun getItemId(position: Int): Long {
//            return data[position].id.toLong()
//        }
//    }
}