package com.sjianjun.reader.module.script

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sjianjun.coroutine.launch
import com.sjianjun.coroutine.withMain
import com.sjianjun.reader.BOOK_SOURCE_ID
import com.sjianjun.reader.BaseAsyncFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.bean.SourceUrl
import com.sjianjun.reader.databinding.DialogEditTextBinding
import com.sjianjun.reader.databinding.DialogEditTextSourceUrlBinding
import com.sjianjun.reader.databinding.MainFragmentBookScriptManagerBinding
import com.sjianjun.reader.databinding.ScriptItemFragmentManagerJavaScriptBinding
import com.sjianjun.reader.module.bookcity.WebViewBrowserActivity
import com.sjianjun.reader.popup.ErrorMsgPopup
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.gone
import com.sjianjun.reader.utils.hideKeyboard
import com.sjianjun.reader.utils.id
import com.sjianjun.reader.utils.setTextColorRes
import com.sjianjun.reader.utils.show
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.click
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import sjj.alog.Log
import java.util.concurrent.Executors

class BookSourceManagerFragment : BaseAsyncFragment() {
    private val sourceUri = MutableLiveData<String>()
    private val importSource = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        sourceUri.postValue(uri.toString())
    }
    private val editLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // refresh list after returning from edit activity
            initData()
        }
    }
    private val viewModel by viewModels<BookSourceManagerViewModel>()
    private var binding: MainFragmentBookScriptManagerBinding? = null
    private var linksAdapter = LinkAdapter(this)
    private val dispatcher by lazy { Executors.newFixedThreadPool(8).asCoroutineDispatcher() }
    private val adapter = Adapter(this@BookSourceManagerFragment)
    private var searchView: SearchView? = null
    private lateinit var popupMenu: PopupMenu
    override fun getLayoutRes() = R.layout.main_fragment_book_script_manager
    override val onLoadedView: (View) -> Unit = {
        setHasOptionsMenu(true)
        binding = MainFragmentBookScriptManagerBinding.bind(it)
        binding!!.recycleView.adapter = adapter
        binding!!.recycleViewLinks.adapter = linksAdapter
        // setup tabs
        binding!!.tabLayout.addTab(binding!!.tabLayout.newTab().setText("订阅链接"))
        binding!!.tabLayout.addTab(binding!!.tabLayout.newTab().setText("书源规则"))
        binding!!.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                val pos = tab?.position ?: 0
                if (pos == 0) {
                    binding!!.recycleViewLinks.visibility = View.VISIBLE
                    binding!!.recycleView.visibility = View.GONE
                    binding!!.bottomBar.visibility = View.GONE
                    initLinksData()
                    activity?.invalidateOptionsMenu()
                } else {
                    binding!!.recycleViewLinks.visibility = View.GONE
                    binding!!.recycleView.visibility = View.VISIBLE
                    binding!!.bottomBar.visibility = View.VISIBLE
                    initData()
                    activity?.invalidateOptionsMenu()
                }
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
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
        setOnBackPressed {
            if (binding?.tabLayout?.selectedTabPosition == 0) {
                // 书源链接页，直接返回
                return@setOnBackPressed false
            }
            val query = searchView?.query.toString().trim()
            if (query.isNotBlank()) {
                searchView?.setQuery("", false)
                query()
                return@setOnBackPressed true
            }
            false
        }
        initLinksData()
        // default select rules tab
        binding!!.tabLayout.getTabAt(0)?.select()
    }

    private fun initLinksData() {
        val list = globalConfig.bookSourceListUser.toMutableList()
        linksAdapter.data.clear()
        linksAdapter.data.addAll(list)
        linksAdapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        setHasOptionsMenu(false)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (binding?.tabLayout?.selectedTabPosition == 0) {
            inflater.inflate(R.menu.main_fragment_book_source_menu1, menu)
        } else {
            inflater.inflate(R.menu.main_fragment_book_source_menu, menu)
        }

        val searchView = menu.findItem(R.id.search_view)?.actionView as? SearchView
        this.searchView = searchView
        if (searchView != null) {
            searchView.queryHint = "书源搜索"
            searchView.imeOptions = EditorInfo.IME_ACTION_SEARCH
            searchView.isIconified = false
            searchView.clearFocus()
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
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        searchView.hideKeyboard()
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.source_help -> {
                val sourceHelp = "https://github.com/SJJ-dot/Reader/blob/BookSource/test/README.MD"
                WebViewBrowserActivity.startActivity(requireContext(), sourceHelp)
                true
            }

            R.id.source_import -> {
                val bindingDialog = DialogEditTextSourceUrlBinding.inflate(LayoutInflater.from(requireContext()))
                sourceUri.observe(viewLifecycleOwner) {
                    if (it.isNullOrBlank()) return@observe
                    bindingDialog.editView.setText(it)
                }
                bindingDialog.tvImportLocal.click {
                    importSource.launch(arrayOf("*/*"))
                }
                val dialog = MaterialAlertDialogBuilder(requireContext())
                    .setOnDismissListener {
                        sourceUri.removeObservers(viewLifecycleOwner)
                        sourceUri.postValue("")
                    }
                    .setView(bindingDialog.root)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    bindingDialog.editView.hideKeyboard()
                    val url = bindingDialog.editView.text.toString().trim()
                    if (url.isBlank()) {
                        toast("请输入书源地址")
                        return@setOnClickListener
                    }
                    val group = bindingDialog.etGroup.text.toString().trim()
                    if (group.isBlank()) {
                        toast("请输入书源分组")
                        return@setOnClickListener
                    }
                    val sourceUrl = SourceUrl(url, group)
                    if (sourceUrl.url.startsWith("http")) {
                        val bookSourceListUser = globalConfig.bookSourceListUser.toMutableList()
                        bookSourceListUser.removeAll { it.url == sourceUrl.url }
                        bookSourceListUser.add(0, sourceUrl)
                        globalConfig.bookSourceListUser = bookSourceListUser
                        initLinksData()
                    } else {
                        import(listOf(sourceUrl))
                    }
                    dialog.dismiss()
                }
                true
            }

            R.id.source_enable -> {
                searchView?.setQuery("已启用", false)
                query()
                true
            }

            R.id.source_disable -> {
                searchView?.setQuery("已禁用", false)
                query()
                true
            }

            R.id.source_all -> {
                searchView?.setQuery("", false)
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

    private fun import(sourceList: List<SourceUrl>) {
        launch {
            try {
                showSnackbar(view, "正在导入书源", Snackbar.LENGTH_INDEFINITE)
                val import = viewModel.import(sourceList)
                if (import > 0) {
                    //书源更新刷新
                    initData()
                    showSnackbar(view, "书源导入成功")
                } else {
                    showSnackbar(view, "书源导入失败")
                }
            } catch (e: Exception) {
                Log.e("书源导入失败", e)
                showSnackbar(view, "书源导入失败：${e.message}")
            }
        }
    }

    private fun query() {
        launch {
            val query = searchView?.query.toString().trim()
            val allJs = viewModel.getAllBookSource()
            adapter.data.clear()
            if (query.isBlank()) {
                adapter.data.addAll(allJs)
            } else {
                val querys = query.split(" ").filter { !it.isBlank() }
                val allJs = allJs.toMutableList()
                querys.forEach { query ->
                    allJs.removeAll { js ->
                        when (query) {
                            "已启用" -> !js.enable
                            "已禁用" -> js.enable
                            else -> !(js.id.contains(query, true)
                                    || js.name.contains(query, true)
                                    || js.group.contains(query, true)
                                    || js.comment.contains(query, true)
                                    || js.checkResult?.contains(query, true) == true)
                        }
                    }
                }
                adapter.data.addAll(allJs)
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
            searchView?.clearFocus()
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
                R.id.source_create->{
                    openEditScript("")
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
                                        viewModel.saveJs(deferreds.awaitAll())
                                        withMain {
                                            dismissSnackbar()
                                            val errorList = adapter.data.filter {
                                                it.checkResult?.contains("失败") == true
                                            }
                                            if (errorList.isNotEmpty()) {
                                                searchView?.setQuery("校验失败", true)
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
        searchView?.clearFocus()
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
                val str = "${script.comment.trim()}\n${script.checkResult?.trim() ?: ""}".trim()
                if (str.isBlank()) {
                    binding.ivSourceCheckRes.gone()
                } else {
                    binding.ivSourceCheckRes.show()
                    binding.ivSourceCheckRes.text = str
                    if (script.checkResult?.contains("失败") == true) {
                        binding.ivSourceCheckRes.setTextColorRes(R.color.dn_colorAccent)
                    } else {
                        binding.ivSourceCheckRes.setTextColorRes(R.color.colorPrimary)
                    }
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
                    fragment.openEditScript(script.id)
                }
                binding.root.click { binding.cbBookSource.isChecked = !binding.cbBookSource.isChecked }
            }
        }

        override fun getItemId(position: Int): Long {
            return data[position].id.id
        }
    }

    fun openEditScript(id: String) {
        val intent = Intent(requireContext(), EditJavaScriptActivity::class.java)
        intent.putExtra(BOOK_SOURCE_ID, id)
        editLauncher.launch(intent)
    }

    class LinkAdapter(val fragment: BookSourceManagerFragment) : BaseAdapter<SourceUrl>(R.layout.item_book_source_link) {
        override fun itemLayoutRes(viewType: Int): Int {
            return R.layout.item_book_source_link
        }

        override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, p1: Int) {
            val view = holder.itemView
            val tvGroup = view.findViewById<android.widget.TextView>(R.id.tv_group)
            val tvUrl = view.findViewById<android.widget.TextView>(R.id.tv_url)
            val cbAuto = view.findViewById<android.widget.CheckBox>(R.id.cb_auto_check)
            val btnCheck = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_check)
            val btnDelete = view.findViewById<android.widget.ImageView>(R.id.btn_delete)
            val item = data[p1]
            tvGroup.text = item.group
            tvUrl.text = item.url
            cbAuto.setOnCheckedChangeListener(null)
            cbAuto.isChecked = item.autoUpdate
            cbAuto.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    item.autoUpdate = true
                    globalConfig.bookSourceListUser = (globalConfig.bookSourceListUser.toMutableList().apply {
                        find { it.url == item.url }?.autoUpdate = true
                    })
                } else {
                    item.autoUpdate = false
                    globalConfig.bookSourceListUser = (globalConfig.bookSourceListUser.toMutableList().apply {
                        find { it.url == item.url }?.autoUpdate = false
                    })
                }
            }

            btnDelete.click {
                MaterialAlertDialogBuilder(fragment.requireContext())
                    .setTitle("确定要删除“${item.group}”吗？")
                    .setMessage("地址：${item.url}")
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val list = globalConfig.bookSourceListUser.toMutableList()
                        list.removeAll { it.url == item.url }
                        globalConfig.bookSourceListUser = list
                        fragment.initLinksData()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }

            btnCheck.click { view ->
                fragment.import(listOf(item))
            }
        }
    }

}