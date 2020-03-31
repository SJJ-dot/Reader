package com.sjianjun.reader.module.main.fragment

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.Observer
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.SearchHistory
import com.sjianjun.reader.bean.SearchResult
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.hideKeyboard
import com.sjianjun.reader.utils.showKeyboard
import kotlinx.android.synthetic.main.main_fragment_search.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor

class SearchFragment : BaseFragment() {
    private val searchHistoryList by lazy { DataManager.getAllSearchHistory().toLiveData() }
    override fun getLayoutRes() = R.layout.main_fragment_search

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.fragment_main_search_menu, menu)
        val searchView = menu.findItem(R.id.search_view)?.actionView as SearchView
        searchView.queryHint = "请输入书名或者作者"
        searchView.imeOptions = EditorInfo.IME_ACTION_SEARCH
        init(searchView)
        searchView.isIconified = false
    }

    private fun init(searchView: SearchView) {
        searchRecyclerView.layoutManager = LinearLayoutManager(context)
        val resultBookAdapter = SearchResultBookAdapter()
        searchRecyclerView.adapter = resultBookAdapter
        searchView.setOnCloseListener {
            searchView.hideKeyboard()
            NavHostFragment.findNavController(this@SearchFragment).navigateUp()
        }
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (query.isNullOrEmpty()) return true
                queryActor.offer(query)
                return true
            }

            override fun onQueryTextChange(p0: String?): Boolean {
                return false
            }
        })
        searchView.setOnQueryTextFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                ll_search_history?.visibility = View.VISIBLE
                searchRecyclerView?.visibility = View.INVISIBLE
                v.showKeyboard()
            } else {
                searchRecyclerView?.visibility = View.VISIBLE
                ll_search_history?.visibility = View.INVISIBLE
                v.hideKeyboard()
            }
        }

        searchHistoryList.observe(this@SearchFragment, Observer<List<SearchHistory>> {
            tfl_search_history.removeAllViews()
            it.forEach { history ->
                val tagView = layoutInflater.inflate(
                    R.layout.main_item_fragment_search_history,
                    tfl_search_history,
                    false
                ) as TextView
                tfl_search_history.addView(tagView, 0)
                tagView.text = history.query
                tagView.setOnClickListener { _ ->
                    searchView.setQuery(history.query, true)
                }
                tagView.setOnLongClickListener { _ ->
                    deleteSearchHistoryActor.offer(listOf(history))
                    true
                }
            }
            tv_search_history_clean.setOnClickListener { _ ->
                deleteSearchHistoryActor.offer(it)
            }
        })
    }


    private val queryActor = actor<String>(capacity = Channel.CONFLATED) {
        while (true) {
            DataManager.search(receive())
        }
    }

    private val deleteSearchHistoryActor = actor<List<SearchHistory>>() {
        while (true) {
            DataManager.deleteSearchHistory(receive())
        }
    }

    private class SearchResultBookAdapter : BaseAdapter() {
        var data = listOf<SearchResult>()
        override fun getItemCount(): Int = data.size
        override fun itemLayoutRes(viewType: Int): Int {
            return R.layout.main_item_fragment_search_result
        }

        override fun onBindViewHolder(
            holder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
            position: Int
        ) {
//            val bind = DataBindingUtil.bind<ItemBookSearchListBinding>(holder.itemView)
            val bookGroup = data[position]

//            bookGroup.bookCover.onBackpressureLatest().observeOnMain().subscribe {
//                Glide.with(this@SearchFragment)
//                    .applyDefaultRequestOptions(requestOptions)
//                    .load(it)
//                    .into(holder.itemView.bookCover)
//            }

            holder.itemView.setOnClickListener { _ ->
                //todo
            }
        }

    }

}