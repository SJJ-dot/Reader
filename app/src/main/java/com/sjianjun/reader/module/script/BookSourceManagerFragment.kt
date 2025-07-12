package com.sjianjun.reader.module.script

import android.annotation.SuppressLint
import android.view.*
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sjianjun.coroutine.launch
import com.sjianjun.coroutine.withMain
import com.sjianjun.reader.BOOK_SOURCE_ID
import com.sjianjun.reader.BaseAsyncFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.URL_BOOK_SOURCE_DEF
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.databinding.DialogEditTextBinding
import com.sjianjun.reader.databinding.MainFragmentBookScriptManagerBinding
import com.sjianjun.reader.databinding.ScriptItemFragmentManagerJavaScriptBinding
import com.sjianjun.reader.popup.ErrorMsgPopup
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.*
import com.sjianjun.reader.view.click
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import sjj.alog.Log
import java.util.concurrent.Executors

class BookSourceManagerFragment : BaseAsyncFragment() {
    private val viewModel by viewModels<BookSourceManagerViewModel>()
    private var binding: MainFragmentBookScriptManagerBinding? = null
    private val dispatcher by lazy { Executors.newFixedThreadPool(8).asCoroutineDispatcher() }
    private val adapter = Adapter(this@BookSourceManagerFragment)
    private lateinit var searchView: SearchView
    private lateinit var popupMenu: PopupMenu
    override fun getLayoutRes() = R.layout.main_fragment_book_script_manager
    override val onLoadedView: (View) -> Unit = {
        setHasOptionsMenu(true)
        binding = MainFragmentBookScriptManagerBinding.bind(it)
        binding!!.recycleView.adapter = adapter
        adapter.onSelectSource = {
            refreshSelectAll()
        }
        binding!!.sourceMenu.click(block = this::showPopupMenu)
        binding!!.cbSelectAll.setOnCheckedChangeListener { _, b ->
            var isChange = false
            if (b || adapter.data.find { !it.selected } == null) {
                adapter.data.forEach {
                    if (it.selected != b) {
                        it.selected = b
                        isChange = true
                    }
                }
            }
            if (isChange) adapter.notifyDataSetChanged()
            refreshSelectAll()
        }
        binding!!.sourceReverse.click {
            adapter.data.forEach {
                it.selected = !it.selected
            }
            adapter.notifyDataSetChanged()
            refreshSelectAll()
        }
        binding!!.sourceDelete.click {
            val list = adapter.data.filter { it.selected }
            if (list.isEmpty()) {
                toast("请选择要删除的书源")
                return@click
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("确认删除")
                .setMessage("确定要删除选中的${list.size}个书源吗？")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    launch {
                        viewModel.delete(list)
                        adapter.data.removeAll(list)
                        adapter.notifyDataSetChanged()
                        refreshSelectAll()
                    }
                }.setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        initData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        setHasOptionsMenu(false)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main_fragment_book_source_menu, menu)
        val searchView = menu.findItem(R.id.search_view)?.actionView as SearchView
        searchView.queryHint = "书源搜索"
        searchView.imeOptions = EditorInfo.IME_ACTION_SEARCH
        searchView.isIconified = false
        searchView.clearFocus()
//        searchView.isSubmitButtonEnabled = true
        this.searchView = searchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

            override fun onQueryTextSubmit(query: String?): Boolean {
                query()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                query()
                return false
            }
        })
        searchView.setOnCloseListener {
            searchView.hideKeyboard()
            searchView.clearFocus()
            true
        }
        searchView.setOnQueryTextFocusChangeListener { view, b ->
            if (!b) {
                searchView.hideKeyboard()
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        searchView.hideKeyboard()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.source_import -> {
                val bindingDialog = DialogEditTextBinding.inflate(LayoutInflater.from(requireContext()))
                bindingDialog.editView.apply {
                    val urls = mutableListOf<String>()

                    val bookSourceListUser = globalConfig.bookSourceListUser.toMutableList()
                    urls.addAll(bookSourceListUser)

                    val urlDef = globalConfig.bookSourceDef
                    if (urlDef.isNotBlank() || urls.isEmpty()) {
                        urls.add(URL_BOOK_SOURCE_DEF)
                    }
                    setFilterValues(urls)
                    delCallBack = {
                        if (it == urlDef) {
                            globalConfig.bookSourceDef = ""
                        }
                        if (bookSourceListUser.remove(it)) {
                            globalConfig.bookSourceListUser = bookSourceListUser
                        }
                    }
                }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("书源导入")
                    .setView(bindingDialog.root)
                    .setPositiveButton(android.R.string.ok) { dialog, _ ->
                        bindingDialog.editView.hideKeyboard()
                        val url = bindingDialog.editView.text.toString().trim()
                        if (url.isBlank()) {
                            toast("请输入书源地址")
                            return@setPositiveButton
                        }
                        launch {
                            try {
                                showSnackbar(binding!!.recycleView, "正在导入书源", Snackbar.LENGTH_INDEFINITE)
                                val import = viewModel.import(listOf(url))
                                if (import > 0) {
                                    initData()
                                    showSnackbar(binding!!.recycleView, "书源导入成功")
                                    if (url == URL_BOOK_SOURCE_DEF) {
                                        globalConfig.bookSourceDef = url
                                    } else {
                                        val bookSourceListUser = globalConfig.bookSourceListUser.toMutableList()
                                        if (!bookSourceListUser.contains(url)) {
                                            bookSourceListUser.add(0, url)
                                            globalConfig.bookSourceListUser = bookSourceListUser
                                        }
                                    }
                                } else {
                                    showSnackbar(binding!!.recycleView, "书源导入失败")
                                }

                            } catch (e: Exception) {
                                Log.e("书源导入失败", e)
                                showSnackbar(binding!!.recycleView, "书源导入失败：${e.message}")
                            }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }

            R.id.source_enable -> {
                searchView.setQuery("已启用", false)
                query()
                true
            }

            R.id.source_disable -> {
                searchView.setQuery("已禁用", false)
                query()
                true
            }

            R.id.source_all -> {
                searchView.setQuery("", false)
                query()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initData() {
        launch {
            val allJs = viewModel.getAllBookSource()
            adapter.data.clear()
            adapter.data.addAll(allJs)
            adapter.notifyDataSetChanged()
            refreshSelectAll()
        }
    }


    private fun query() {
        launch {
            val query = searchView.query.toString().trim()
            val allJs = viewModel.getAllBookSource()
            adapter.data.clear()
            if (query.isBlank()) {
                adapter.data.addAll(allJs)
            } else {
                adapter.data.addAll(allJs.filter {
                    if (query == "已启用") {
                        it.enable
                    } else if (query == "已禁用") {
                        !it.enable
                    } else {
                        it.id.contains(query) || it.checkResult?.contains(query) == true
                    }
                })
            }
            adapter.data.forEach {
                it.selected = true
            }
            adapter.notifyDataSetChanged()
            refreshSelectAll()
        }
    }

    private fun refreshSelectAll() {
        val selected = adapter.data.count { it.selected }
        if (selected == adapter.data.size) {
            binding!!.cbSelectAll.text = "取消（${selected}/${selected}）"
        } else {
            binding!!.cbSelectAll.text = "全选（${selected}/${adapter.data.size}）"
        }
        val selectedAll = adapter.data.find { !it.selected } == null
        if (selectedAll != binding!!.cbSelectAll.isChecked) {
            binding!!.cbSelectAll.isChecked = selectedAll
        }
    }

    private fun showPopupMenu(anchor: View) {
        if (this::popupMenu.isInitialized) {
            searchView.clearFocus()
            popupMenu.show()
            return
        }
        val popupMenu = PopupMenu(anchor.context, anchor)
        popupMenu.menuInflater.inflate(R.menu.main_fragment_book_source_menu_bottom, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.source_enable_select -> {
                    val list = adapter.data.filter { it.selected }
                    if (list.isEmpty()) {
                        toast("请选择书源")
                    } else {
                        launch {
                            list.forEach { it.enable = true }
                            viewModel.saveJs(list)
                            adapter.notifyDataSetChanged()
                        }
                    }
                }

                R.id.source_disable_select -> {
                    val list = adapter.data.filter { it.selected }
                    if (list.isEmpty()) {
                        toast("请选择书源")
                    } else {
                        launch {
                            list.forEach { it.enable = false }
                            viewModel.saveJs(list)
                            adapter.notifyDataSetChanged()
                        }
                    }
                }

                R.id.source_check_select -> {
                    val list = adapter.data.filter { it.selected }
                    if (list.isEmpty()) {
                        toast("请选择书源")
                    } else {
                        val bindingDialog = DialogEditTextBinding.inflate(LayoutInflater.from(requireContext()))
                        bindingDialog.editView.setText("我的")
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("测试搜索书名、作者")
                            .setView(bindingDialog.root)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                val key = bindingDialog.editView.text.toString().trim()
                                if (key.isBlank()) {
                                    toast("请输入测试搜索书名、作者")
                                } else {
                                    launch {
                                        var count = 0
                                        showSnackbar(binding!!.recycleView, "书源校验中，请稍后……(0/${list.size})", Snackbar.LENGTH_INDEFINITE)
                                        val deferreds = list.map { js ->
                                            async(dispatcher) {
                                                viewModel.check(js, key)
                                                js
                                            }
                                        }.onEach { deferred ->
                                            deferred.invokeOnCompletion {
                                                launch {
                                                    val source = deferred.await()
                                                    if (++count == list.size) {
                                                        showSnackbar(binding!!.recycleView, "书源校验完成(${count}/${list.size})", Snackbar.LENGTH_INDEFINITE)
                                                    } else {
                                                        showSnackbar(binding!!.recycleView, "书源校验中，请稍后……(${count}/${list.size})", Snackbar.LENGTH_INDEFINITE)
                                                    }
                                                    val index = adapter.data.indexOf(source)
                                                    if (index != -1) adapter.notifyItemChanged(index)
                                                }
                                            }
                                        }
                                        deferreds.awaitAll()
                                        withMain {
                                            dismissSnackbar()
                                            val errorList = adapter.data.filter {
                                                it.checkResult?.contains("失败") == true
                                            }
                                            if (errorList.isNotEmpty()) {
                                                searchView.setQuery("校验失败", true)
                                                query()
                                            }
                                        }

                                    }
                                }
                            }.setNegativeButton(android.R.string.cancel, null)
                            .show()


                    }
                }
            }

            true
        }
        searchView.clearFocus()
        popupMenu.show()
        this.popupMenu = popupMenu
    }


    class Adapter(val fragment: BookSourceManagerFragment) : BaseAdapter<BookSource>() {
        lateinit var onSelectSource: () -> Unit

        init {
            setHasStableIds(true)
        }

        override fun itemLayoutRes(viewType: Int): Int {
            return R.layout.script_item_fragment_manager_java_script
        }

        override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, p1: Int) {
            val binding = ScriptItemFragmentManagerJavaScriptBinding.bind(holder.itemView)
            holder.itemView.apply {
                val script = data[p1]
                binding.tvSourceName.text = "${script.group}-${script.name}"
                binding.cbBookSource.setOnCheckedChangeListener(null)
                binding.cbBookSource.isChecked = script.selected
                binding.cbBookSource.setOnCheckedChangeListener { _, b ->
                    script.selected = b
                    onSelectSource()
                }
                if (script.checkResult.isNullOrBlank()) {
                    binding.ivSourceCheckRes.gone()
                } else {
                    binding.ivSourceCheckRes.show()
                    binding.ivSourceCheckRes.text = "${script.checkResult}"
                }
                binding.swSourceEnable.setOnCheckedChangeListener(null)
                binding.swSourceEnable.isChecked = script.enable

                binding.swSourceEnable.setOnCheckedChangeListener { view, isChecked ->
                    fragment.launch {
                        script.enable = isChecked
                        fragment.viewModel.saveJs(listOf(script))
                    }
                }
                if (script.checkErrorMsg.isNullOrBlank()) {
                    binding.ivErrorHint.gone()
                    binding.ivErrorHint.setOnClickListener(null)
                } else {
                    binding.ivErrorHint.show()
                    binding.ivErrorHint.click {
                        val popup = ErrorMsgPopup(fragment.context)
                            .init("${script.checkErrorMsg}")
                            .setPopupGravity(Gravity.BOTTOM)
                        popup.showPopupWindow()
                    }
                }
                binding.ivEditSource.click {
                    fragment.startActivity<EditJavaScriptActivity>(BOOK_SOURCE_ID, script.id)
                }
            }
        }

        override fun getItemId(position: Int): Long {
            return data[position].id.id
        }
    }
}