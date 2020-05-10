package com.sjianjun.reader.module.search

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.reader.BaseAsyncFragment
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.SearchHistory
import com.sjianjun.reader.bean.SearchResult
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import kotlinx.android.synthetic.main.main_item_fragment_search_history.view.*
import kotlinx.android.synthetic.main.main_item_fragment_search_result.view.*
import kotlinx.android.synthetic.main.search_fragment_search.*
import kotlinx.android.synthetic.main.search_item_fragment_search_hint.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sjj.alog.Log
import java.util.concurrent.atomic.AtomicInteger

class SearchFragment : BaseAsyncFragment() {
    private val searchKey by lazy { arguments?.getString(SEARCH_KEY) }
    private val searchResult = MutableLiveData<List<List<SearchResult>>>()
    private val searchHint = SearchHintAdapter()
    private lateinit var deleteSearchHistoryActor: SendChannel<List<SearchHistory>>
    private lateinit var queryActor: SendChannel<String>
    private lateinit var queryHintActor: SendChannel<String>

    override fun getLayoutRes() = R.layout.search_fragment_search


    override val onLoadedView: (View) -> Unit = {
        setHasOptionsMenu(true)
        recycle_view_hint.adapter = searchHint
        deleteSearchHistoryActor = deleteSearchHistoryActor()
        queryActor = queryActor()
        queryHintActor = queryHintActor()
        initData()
    }

    override val onDestroy: () -> Unit = {
        setHasOptionsMenu(false)
    }

    private fun initData() {
        launchIo {
            val javaScriptList = DataManager.getAllJavaScript().first().filter { it.enable }
            search_refresh.max = javaScriptList.size
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main_menu_fragment_search, menu)
        val searchView = menu.findItem(R.id.search_view)?.actionView as SearchView
        searchView.queryHint = "请输入书名或者作者"
        searchView.imeOptions = EditorInfo.IME_ACTION_SEARCH
        init(searchView)
        searchView.isIconified = false
        searchHint.itemClick = {
            searchView.setQuery(it, true)
        }

        if (searchKey?.isNotEmpty() == true) {
            searchView.setQuery(searchKey, true)
        }

        if (searchResult.value?.isNotEmpty() == true) {
            searchView.clearFocus()
        }
    }

    private fun init(searchView: SearchView) {
        initSearchView(searchView)
        initSearchResultList(searchView)
        initSearchHistory(searchView)
    }

    private fun initSearchHistory(searchView: SearchView) {
        Log.i("view is null ${tfl_search_history == null}")
        tfl_search_history ?: return
        launch {
            DataManager.getAllSearchHistory().collectLatest {
                tfl_search_history.removeAllViews()
                it?.forEach { history ->
                    val tagView = layoutInflater.inflate(
                        R.layout.main_item_fragment_search_history,
                        tfl_search_history,
                        false
                    )
                    tfl_search_history.addView(tagView, 0)
                    tagView.search_history_text.text = history.query
                    tagView.search_history_text.setOnClickListener { _ ->
                        searchView.setQuery(history.query, true)
                    }
                    tagView.search_history_text.setOnLongClickListener { _ ->
                        deleteSearchHistoryActor.offer(listOf(history))
                        true
                    }
                }
                tv_search_history_clean.setOnClickListener { _ ->
                    deleteSearchHistoryActor.offer(it ?: return@setOnClickListener)
                }
            }
        }
    }

    private fun initSearchView(searchView: SearchView) {
        searchView.setOnCloseListener {
            searchView.hideKeyboard()
            NavHostFragment.findNavController(this@SearchFragment).navigateUp()
        }
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (query.isNullOrEmpty()) return true
                queryActor.offer(query)
                searchResult.postValue(emptyList())
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty()) {
                    searchHint.data = emptyList()
                    searchHint.notifyDataSetChanged()
                }
                launch {
                    queryHintActor.send(newText ?: "")
                }
                return false
            }
        })
        searchView.setOnQueryTextFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                hideProgress()
                searchRecyclerView?.hide()
                ll_search_history?.show()
                v.showKeyboard()
            } else {
                searchRecyclerView?.show()
                ll_search_history?.hide()
                v.hideKeyboard()
                search_refresh?.run {
                    if (progress in 1 until max) {
                        showProgress()
                    }
                }
            }
        }
    }

    private fun initSearchResultList(searchView: SearchView) {
        searchRecyclerView?.apply {
            searchRecyclerView?.layoutManager = LinearLayoutManager(context)
            val resultBookAdapter = SearchResultBookAdapter(this@SearchFragment)
            resultBookAdapter.setHasStableIds(true)
            searchRecyclerView.adapter = resultBookAdapter
            searchResult.observe(viewLifecycleOwner, Observer {
                if (!(resultBookAdapter.data.isEmpty() && it.isEmpty())) {
                    resultBookAdapter.data = it
                    resultBookAdapter.notifyDataSetChanged()
                }
            })
        }

    }

    //宜搜快速提示
    private fun queryHintActor() =
        lifecycleScope.actor<String>(Dispatchers.IO, capacity = Channel.CONFLATED) {
            var job: Job? = null
            for (msg in channel) {
                job?.cancel()
                job = launch {
                    val hintList = DataManager.searchHint(msg) ?: emptyList()
                    withMain {
                        searchHint.data = hintList
                        searchHint.notifyDataSetChanged()
                    }
                }
            }
        }

    private fun queryActor() =
        lifecycleScope.actor<String>(Dispatchers.IO, capacity = Channel.CONFLATED) {
            var job: Job? = null
            for (msg in channel) {
                job?.cancel()
                job = launch {
                    withMain {
                        showProgress()
                        search_refresh?.progress = 0
                    }
                    val count = AtomicInteger()
                    DataManager.search(msg)?.collectLatest {
                        search_refresh?.progress = count.incrementAndGet()
                        delay(300)
                        searchResult.postValue(it)
                    }
                    withMain {
                        search_refresh?.progress = search_refresh?.max ?: 0
                        hideProgress()
                    }
                }
            }
        }

    private fun showProgress() {
        search_refresh?.animFadeIn()
    }

    private fun hideProgress() {
        search_refresh?.animFadeOut()
    }

    private fun deleteSearchHistoryActor() = lifecycleScope.actor<List<SearchHistory>>() {
        for (msg in channel) {
            DataManager.deleteSearchHistory(msg)
        }
    }

    private class SearchHintAdapter : BaseAdapter(R.layout.search_item_fragment_search_hint) {
        var data = listOf<String>()
        var itemClick: ((String) -> Unit)? = null
        override fun getItemCount() = data.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val hint = data[position]
            holder.itemView.hint.text = hint
            holder.itemView.setOnClickListener {
                itemClick?.invoke(hint)
            }
        }
    }

    private class SearchResultBookAdapter(val fragment: SearchFragment) : BaseAdapter() {
        var data = listOf<List<SearchResult>>()
        override fun getItemCount(): Int = data.size
        override fun itemLayoutRes(viewType: Int): Int {
            return R.layout.main_item_fragment_search_result
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val searchResult = data[position].first()
            holder.itemView.bookCover.glide(fragment, searchResult.bookCover)
            holder.itemView.bookName.text = searchResult.bookTitle
            holder.itemView.author.text = "作者：${searchResult.bookAuthor}"
            holder.itemView.lastChapter.text = "最新章节：${searchResult.latestChapter}"
            holder.itemView.haveRead.text = "来源：${searchResult.source} 共${data[position].size}个源"

            holder.itemView.setOnClickListener { _ ->
                fragment.launch {
                    DataManager.saveSearchResult(data[position])
                    NavHostFragment.findNavController(fragment).navigate(
                        R.id.bookDetailsFragment,
                        bundle(
                            BOOK_TITLE to searchResult.bookTitle,
                            BOOK_AUTHOR to searchResult.bookAuthor
                        )
                    )
                }
            }
        }

        override fun getItemId(position: Int): Long {
            return data[position].first().id
        }

    }

}