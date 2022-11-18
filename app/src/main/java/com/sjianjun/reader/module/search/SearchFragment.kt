package com.sjianjun.reader.module.search

import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.coroutine.launch
import com.sjianjun.coroutine.launchIo
import com.sjianjun.coroutine.withMain
import com.sjianjun.reader.*
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.SearchResult
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.repository.BookSourceMgr
import com.sjianjun.reader.utils.*
import kotlinx.android.synthetic.main.main_item_fragment_search_history.view.*
import kotlinx.android.synthetic.main.main_item_fragment_search_result.view.*
import kotlinx.android.synthetic.main.search_fragment_search.*
import kotlinx.android.synthetic.main.search_item_fragment_search_hint.view.*
import kotlinx.coroutines.flow.collectLatest
import sjj.alog.Log
import java.util.concurrent.atomic.AtomicInteger

class SearchFragment : BaseAsyncFragment() {
    private val searchKey by lazy { arguments?.getString(SEARCH_KEY) }
    private val searchResult = MutableLiveData<List<List<SearchResult>>>()
    private val searchHint = SearchHintAdapter()

    override fun getLayoutRes() = R.layout.search_fragment_search


    override val onLoadedView: (View) -> Unit = {
        setHasOptionsMenu(true)
        recycle_view_hint.adapter = searchHint
        initData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        setHasOptionsMenu(false)
    }

    private fun initData() {
        launchIo {
            val javaScriptList = BookSourceMgr.getAllEnableBookSource()
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

    private fun initSearchHistory(searchView: SearchView) =
        launch(singleCoroutineKey = "initSearchHistory") {
            Log.i("view is null ${tfl_search_history == null}")
            tfl_search_history ?: return@launch
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
                        launchIo(singleCoroutineKey = "delete_history") {
                            DataManager.deleteSearchHistory(listOf(history))
                        }
                        true
                    }
                }
                tv_search_history_clean.setOnClickListener { _ ->
                    launchIo(singleCoroutineKey = "delete_history") {
                        DataManager.deleteSearchHistory(it)
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
                launch(singleCoroutineKey = "search_result") {
                    search(query)
                }
                searchResult.postValue(emptyList())
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty()) {
                    searchHint.data.clear()
                    searchHint.notifyDataSetChanged()
                }
                launch(singleCoroutineKey = "quick_search_hint") {
                    val hintList = DataManager.searchHint(newText ?: "") ?: emptyList()
                    searchHint.data.clear()
                    searchHint.data.addAll(hintList)
                    searchHint.notifyDataSetChanged()
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
                    resultBookAdapter.data.clear()
                    resultBookAdapter.data.addAll(it)
                    resultBookAdapter.notifyDataSetChanged()
                }
            })
        }

    }

    private suspend fun search(searchKeyWord: String) = withMain {
        showProgress()
        search_refresh?.progress = 0
        val count = AtomicInteger()
        DataManager.search(searchKeyWord).debounce(300).collect {
            search_refresh?.progress = count.incrementAndGet()
            searchResult.postValue(it)
        }
        search_refresh?.progress = search_refresh?.max ?: 0
        hideProgress()
    }

    private fun showProgress() {
        search_refresh?.animFadeIn()
    }

    private fun hideProgress() {
        search_refresh?.animFadeOut()
    }

    private class SearchHintAdapter :
        BaseAdapter<String>(R.layout.search_item_fragment_search_hint) {
        var itemClick: ((String) -> Unit)? = null

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val hint = data[position]
            holder.itemView.hint.text = hint
            holder.itemView.setOnClickListener {
                itemClick?.invoke(hint)
            }
        }
    }

    private class SearchResultBookAdapter(val fragment: SearchFragment) :
        BaseAdapter<List<SearchResult>>() {

        override fun itemLayoutRes(viewType: Int): Int {
            return R.layout.main_item_fragment_search_result
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val searchResult = data[position].first()
            holder.itemView.bookCover.glide(fragment, searchResult.bookCover)
            holder.itemView.bookName.text = searchResult.bookTitle
            holder.itemView.author.text = "作者：${searchResult.bookAuthor}"
            holder.itemView.lastChapter.text = "最新章节：${searchResult.latestChapter}"
            holder.itemView.haveRead.text = "来源：${searchResult.bookSource?.group}-${searchResult.bookSource?.name} 共${data[position].size}个源"

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