package com.sjianjun.reader.module.main.fragment

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.SearchView
import androidx.navigation.fragment.NavHostFragment
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.SearchResult
import com.sjianjun.reader.rhino.js
import com.sjianjun.reader.utils.hideKeyboard
import com.sjianjun.reader.utils.showKeyboard
import kotlinx.android.synthetic.main.main_fragment_search.*

class SearchFragment : BaseFragment() {
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
        searchRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        val resultBookAdapter = SearchResultBookAdapter()
        searchRecyclerView.adapter = resultBookAdapter
        searchView.setOnCloseListener {
            searchView.hideKeyboard()
            NavHostFragment.findNavController(this@SearchFragment).navigateUp()
        }
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (query.isNullOrEmpty()) return true
//                model.addSearchHistory(SearchHistory(content = p0)).subscribe()
//                searchView.clearFocus()
//                refresh_progress_bar.isAutoLoading = true
//                model.search(p0).observeOnMain().doAfterTerminate {
//                    refresh_progress_bar.isAutoLoading = false
//                }.subscribe({ ls ->
//                    resultBookAdapter.data = ls
//                    resultBookAdapter.notifyDataSetChanged()
//                }, {
//                    toastSHORT("${it.message}")
//                }).destroy("searchBook")
                js {

                }
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

//        model = getModel()

//        model.getSearchHistory().observeOnMain().subscribe { history ->
//            tfl_search_history.removeAllViews()
//            history.forEach {
//                val tagView = layoutInflater.inflate(R.layout.item_search_history, tfl_search_history, false) as TextView
//                tfl_search_history.addView(tagView)
//                tagView.text = it.content
//                tagView.setOnClickListener { _ ->
//                    searchView.setQuery(it.content, true)
//                }
//                tagView.setOnLongClickListener { _ ->
//                    model.deleteSearchHistory(listOf(it)).subscribe()
//                    true
//                }
//            }
//            tv_search_history_clean.setOnClickListener {
////                model.deleteSearchHistory(history).subscribe()
//            }
//        }.destroy("get Search History")
    }


    private inner class SearchResultBookAdapter : BaseAdapter() {
        var data = listOf<SearchResult>()
        override fun getItemCount(): Int = data.size
        override fun itemLayoutRes(viewType: Int): Int {
            return R.layout.main_item_fragment_search_result
        }

        override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
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