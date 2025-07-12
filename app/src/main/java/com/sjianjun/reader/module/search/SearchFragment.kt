package com.sjianjun.reader.module.search

import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.coroutine.launch
import com.sjianjun.coroutine.launchIo
import com.sjianjun.coroutine.withMain
import com.sjianjun.reader.BOOK_TITLE
import com.sjianjun.reader.BaseAsyncFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.SEARCH_KEY
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.SearchHistory
import com.sjianjun.reader.bean.SearchResult
import com.sjianjun.reader.databinding.MainItemFragmentSearchHistoryBinding
import com.sjianjun.reader.databinding.MainItemFragmentSearchResultBinding
import com.sjianjun.reader.databinding.SearchFragmentSearchBinding
import com.sjianjun.reader.databinding.SearchItemFragmentSearchHintBinding
import com.sjianjun.reader.utils.animFadeIn
import com.sjianjun.reader.utils.animFadeOut
import com.sjianjun.reader.utils.bundle
import com.sjianjun.reader.utils.glide
import com.sjianjun.reader.utils.hide
import com.sjianjun.reader.utils.hideKeyboard
import com.sjianjun.reader.utils.id
import com.sjianjun.reader.utils.show
import com.sjianjun.reader.utils.showKeyboard
import com.sjianjun.reader.view.click
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import sjj.alog.Log
import java.util.concurrent.atomic.AtomicInteger

class SearchFragment : BaseAsyncFragment() {
    private var searchKey: String? = null
    private val searchResult = MutableLiveData<List<List<SearchResult>>>()
    private val searchHint = SearchHintAdapter()
    private var binding: SearchFragmentSearchBinding? = null
    private val viewModel by viewModels<SearchViewModel>()
    private var searchView: SearchView? = null
    override fun getLayoutRes() = R.layout.search_fragment_search


    override val onLoadedView: (View) -> Unit = {
        setHasOptionsMenu(true)
        binding = SearchFragmentSearchBinding.bind(it)
        binding!!.recycleViewHint.adapter = searchHint
        initData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        setHasOptionsMenu(false)
    }

    private fun initData() {
        launchIo {
            val javaScriptList = viewModel.getAllEnableBookSource()
            binding!!.searchRefresh.max = javaScriptList.size
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main_menu_fragment_search, menu)
        val searchView = menu.findItem(R.id.search_view)?.actionView as SearchView
        searchView.queryHint = "请输入书名或者详情页地址"
        searchView.imeOptions = EditorInfo.IME_ACTION_SEARCH
        init(searchView)
        searchView.isIconified = false
        searchHint.itemClick = {
            searchView.setQuery(it, true)
        }

        if (searchKey?.isNotEmpty() == true) {
            searchView.setQuery(searchKey, false)
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

    private fun addHistoryView(history: SearchHistory) {
        val tagViewBinding = MainItemFragmentSearchHistoryBinding.inflate(layoutInflater, binding?.tflSearchHistory, false)
        binding?.tflSearchHistory?.addView(tagViewBinding.root, 0)
        tagViewBinding.searchHistoryText.text = history.query
        tagViewBinding.searchHistoryText.click { _ ->
            searchView?.setQuery(history.query, true)
        }
        tagViewBinding.searchHistoryText.setOnLongClickListener { _ ->
            viewModel.deleteSearchHistory(listOf(history))
            true
        }
    }

    private fun initSearchHistory(searchView: SearchView) = launch(singleCoroutineKey = "initSearchHistory") {
        Log.i("view is null")
        this@SearchFragment.searchView = searchView
        binding?.tflSearchHistory ?: return@launch
        launch {
            viewModel.getAllSearchHistory().collectLatest {
                binding?.tflSearchHistory?.removeAllViews()
                it.forEach { history ->
                    addHistoryView(history)
                }
            }
        }
        binding?.tvSearchHistoryClean?.click { _ ->
            viewModel.deleteAllSearchHistory()
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
                searchKey = query
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
                    val hintList = viewModel.searchHint(newText ?: "") ?: emptyList()
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
                binding?.searchRecyclerView?.hide()
                binding?.llSearchHistory?.show()
                v.showKeyboard()
            } else {
                binding?.searchRecyclerView?.show()
                binding?.llSearchHistory?.hide()
                v.hideKeyboard()
                binding?.searchRefresh?.run {
                    if (progress in 1 until max) {
                        showProgress()
                    }
                }
            }
        }
    }

    private fun initSearchResultList(searchView: SearchView) {
        binding?.searchRecyclerView?.apply {
            binding?.searchRecyclerView?.layoutManager = LinearLayoutManager(context)
            val resultBookAdapter = SearchResultBookAdapter(this@SearchFragment)
            resultBookAdapter.setHasStableIds(true)
            binding?.searchRecyclerView?.adapter = resultBookAdapter
            searchResult.observe(viewLifecycleOwner) {
                if (!(resultBookAdapter.data.isEmpty() && it.isEmpty())) {
                    resultBookAdapter.data.clear()
                    resultBookAdapter.data.addAll(it)
                    resultBookAdapter.notifyDataSetChanged()
                }
            }
        }

    }

    private suspend fun search(searchKeyWord: String) = withMain {
        showProgress()
        binding?.searchRefresh?.progress = 0
        val count = AtomicInteger()
        viewModel.search(searchKeyWord).collect {
            binding?.searchRefresh?.progress = count.incrementAndGet()
            searchResult.postValue(it)
        }
        binding?.searchRefresh?.progress = binding?.searchRefresh?.max ?: 0
        hideProgress()
    }

    private fun showProgress() {
        binding?.searchRefresh?.animFadeIn()
    }

    private fun hideProgress() {
        binding?.searchRefresh?.animFadeOut()
    }

    private class SearchHintAdapter :
        BaseAdapter<String>(R.layout.search_item_fragment_search_hint) {
        var itemClick: ((String) -> Unit)? = null

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val binding = SearchItemFragmentSearchHintBinding.bind(holder.itemView)
            val hint = data[position]
            binding.hint.text = hint
            holder.itemView.click {
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
            val binding = MainItemFragmentSearchResultBinding.bind(holder.itemView)
            val searchResult = data[position].first()
            binding.bookCover.glide(searchResult.bookCover)
            binding.bookName.text = searchResult.bookTitle
            binding.author.text = "作者：${searchResult.bookAuthor}"
            binding.haveRead.text = "${searchResult.bookSource?.group}：${searchResult.bookSource?.name} 共${data[position].size}个"

            holder.itemView.click { _ ->
                fragment.launch {
                    fragment.viewModel.saveSearchResult(data[position])
                    NavHostFragment.findNavController(fragment).navigate(
                        R.id.bookDetailsFragment,
                        bundle(BOOK_TITLE to searchResult.bookTitle)
                    )
                }
            }
        }

        override fun getItemId(position: Int): Long {
            return data[position].first().id
        }

    }

}