package com.sjianjun.reader.module.script

import android.annotation.SuppressLint
import android.view.*
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sjianjun.coroutine.launch
import com.sjianjun.coroutine.withMain
import com.sjianjun.reader.BaseAsyncFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.popup.ErrorMsgPopup
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.BookSourceManager
import com.sjianjun.reader.utils.*
import kotlinx.android.synthetic.main.dialog_edit_text.view.*
import kotlinx.android.synthetic.main.main_fragment_book_script_manager.*
import kotlinx.android.synthetic.main.script_item_fragment_manager_java_script.view.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import sjj.alog.Log
import splitties.views.inflate
import java.util.concurrent.Executors

class BookScriptManagerFragment : BaseAsyncFragment() {
    private val dispatcher by lazy { Executors.newFixedThreadPool(8).asCoroutineDispatcher() }
    private val adapter = Adapter(this@BookScriptManagerFragment)
    private lateinit var searchView: SearchView
    private lateinit var popupMenu: PopupMenu
    override fun getLayoutRes() = R.layout.main_fragment_book_script_manager
    override val onLoadedView: (View) -> Unit = {
        setHasOptionsMenu(true)
        recycle_view.adapter = adapter
        adapter.onSelectSource = {
            refreshSelectAll()
        }
        source_menu.setOnClickListener(this::showPopupMenu)
        cb_select_all.setOnCheckedChangeListener { _, b ->
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
        source_reverse.setOnClickListener {
            adapter.data.forEach {
                it.selected = !it.selected
            }
            adapter.notifyDataSetChanged()
            refreshSelectAll()
        }
        source_delete.setOnClickListener {
            val list = adapter.data.filter { it.selected }
            if (list.isEmpty()) {
                toast("请选择要删除的书源")
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("确认删除")
                .setMessage("确定要删除选中的${list.size}个书源吗？")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    BookSourceManager.delete(*list.toTypedArray())
                    adapter.data.removeAll(list)
                    adapter.notifyDataSetChanged()
                    refreshSelectAll()
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
                val view =
                    LayoutInflater.from(requireContext()).inflate<View>(R.layout.dialog_edit_text)
                view.edit_view.apply {
                    setFilterValues(globalConfig.bookSourceImportUrls)
                    delCallBack = {
                        globalConfig.bookSourceImportUrls.remove(it)
                        globalConfig.bookSourceImportUrls = globalConfig.bookSourceImportUrls
                    }
                }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("书源导入")
                    .setView(view)
                    .setPositiveButton(android.R.string.ok) { dialog, _ ->
                        val url = view.edit_view.text.toString()
                        globalConfig.bookSourceImportUrls.remove(url)
                        if (url.isNotBlank()) {
                            globalConfig.bookSourceImportUrls.add(url)
                        }
                        globalConfig.bookSourceImportUrls = globalConfig.bookSourceImportUrls
                        view.edit_view.hideKeyboard()
                        launch {
                            try {
                                showSnackbar(recycle_view, "正在导入书源", Snackbar.LENGTH_INDEFINITE)
                                BookSourceManager.import(url)
                                initData()
                                showSnackbar(recycle_view, "书源导入成功")
                            } catch (e: Exception) {
                                showSnackbar(recycle_view, "书源导入失败：${e.message}")
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
            val allJs = BookSourceManager.getAllJs()
            adapter.data.clear()
            adapter.data.addAll(allJs)
            adapter.notifyDataSetChanged()
            refreshSelectAll()
        }
    }

    private fun query() {
        launch {
            val query = searchView.query.toString().trim()
            val allJs = BookSourceManager.getAllJs()
            adapter.data.clear()
            if (query.isBlank()) {
                adapter.data.addAll(allJs)
            } else {
                adapter.data.addAll(allJs.filter {
                    if (query == "已启用") {
                        it.enable
                    }else if (query == "已禁用") {
                        !it.enable
                    } else {
                        it.source.contains(query) || it.group.contains(query)
                                || it.checkResult?.contains(query) == true
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
            cb_select_all.text = "取消（${selected}/${selected}）"
        } else {
            cb_select_all.text = "全选（${selected}/${adapter.data.size}）"
        }
        val selectedAll = adapter.data.find { !it.selected } == null
        if (selectedAll != cb_select_all.isChecked) {
            cb_select_all.isChecked = selectedAll
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
                        list.forEach { it.enable = true }
                        BookSourceManager.saveJs(*list.toTypedArray())
                        adapter.notifyDataSetChanged()
                    }
                }
                R.id.source_disable_select -> {
                    val list = adapter.data.filter { it.selected }
                    if (list.isEmpty()) {
                        toast("请选择书源")
                    } else {
                        list.forEach { it.enable = false }
                        BookSourceManager.saveJs(*list.toTypedArray())
                        adapter.notifyDataSetChanged()
                    }
                }
                R.id.source_check_select -> {
                    val list = adapter.data.filter { it.selected }
                    if (list.isEmpty()) {
                        toast("请选择书源")
                    } else {
                        val view = LayoutInflater.from(requireContext())
                            .inflate<View>(R.layout.dialog_edit_text)
                        view.edit_view.setText("我的")
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("测试搜索书名、作者")
                            .setView(view)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                val key = view.edit_view.text.toString().trim()
                                if (key.isBlank()) {
                                    toast("请输入测试搜索书名、作者")
                                } else {
                                    launch {
                                        var count = 0
                                        showSnackbar(
                                            recycle_view,
                                            "书源校验中，请稍后……(0/${list.size})",
                                            Snackbar.LENGTH_INDEFINITE
                                        )
                                        val deferreds = list.map { js ->
                                            async(dispatcher) {
                                                BookSourceManager.check(
                                                    js,
                                                    key
                                                );js
                                            }
                                        }.onEach { deferred ->
                                            deferred.invokeOnCompletion {
                                                launch {
                                                    val source = deferred.await()
                                                    if (++count == list.size) {
                                                        showSnackbar(
                                                            recycle_view,
                                                            "书源校验完成(${count}/${list.size})",
                                                            Snackbar.LENGTH_INDEFINITE
                                                        )
                                                    } else {
                                                        showSnackbar(
                                                            recycle_view,
                                                            "书源校验中，请稍后……(${count}/${list.size})",
                                                            Snackbar.LENGTH_INDEFINITE
                                                        )
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
//                                            searchView.clearFocus()
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


    class Adapter(val fragment: BookScriptManagerFragment) : BaseAdapter<BookSource>() {
        lateinit var onSelectSource: () -> Unit

        init {
            setHasStableIds(true)
        }

        override fun itemLayoutRes(viewType: Int): Int {
            return R.layout.script_item_fragment_manager_java_script
        }

        override fun onBindViewHolder(
            holder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
            p1: Int
        ) {
            holder.itemView.apply {
                val script = data[p1]
                tv_source_name.text = "${script.source}"
                cb_book_source.setOnCheckedChangeListener(null)
                cb_book_source.isChecked = script.selected
                cb_book_source.setOnCheckedChangeListener { _, b ->
                    script.selected = b
                    onSelectSource()
                }
                if (script.checkResult.isNullOrBlank()) {
                    iv_source_group.text = script.group
                } else {
                    iv_source_group.text = "${script.group}(${script.checkResult})"
                }
                iv_edit_source.setOnClickListener {
                    fragment.startActivity<EditJavaScriptActivity>(
                        JAVA_SCRIPT_SOURCE,
                        script.source
                    )
                }
                sw_source_enable.setOnCheckedChangeListener(null)
                sw_source_enable.isChecked = script.enable

                sw_source_enable.setOnCheckedChangeListener { view, isChecked ->
                    fragment.launch {
                        script.enable = isChecked
                        BookSourceManager.saveJs(script)
                    }
                }
                if (script.checkErrorMsg.isNullOrBlank()) {
                    iv_error_hint.hide()
                    iv_error_hint.setOnClickListener(null)
                } else {
                    iv_error_hint.show()
                    iv_error_hint.setOnClickListener{
                        val popup = ErrorMsgPopup(fragment.context)
                            .init("${script.checkErrorMsg}")
                            .setPopupGravity(Gravity.TOP or Gravity.START)
                        popup.showPopupWindow(it)
                    }
                }
            }
        }

        override fun getItemId(position: Int): Long {
            return data[position].id
        }
    }
}