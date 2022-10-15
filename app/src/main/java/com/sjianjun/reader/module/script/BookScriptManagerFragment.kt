package com.sjianjun.reader.module.script

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BaseAsyncFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.JsManager
import com.sjianjun.reader.utils.*
import kotlinx.android.synthetic.main.dialog_edit_text.view.*
import kotlinx.android.synthetic.main.main_fragment_book_script_manager.*
import kotlinx.android.synthetic.main.script_item_fragment_manager_java_script.view.*
import sjj.alog.Log
import splitties.views.inflate

class BookScriptManagerFragment : BaseAsyncFragment() {

    private val adapter = Adapter(this@BookScriptManagerFragment)

    override fun getLayoutRes() = R.layout.main_fragment_book_script_manager
    override val onLoadedView: (View) -> Unit = {
        setHasOptionsMenu(true)
        recycle_view.adapter = adapter
        adapter.onSelectSource = {
            val selectedAll = adapter.data.find { !it.selected } == null
            if (selectedAll != cb_select_all.isChecked) {
                cb_select_all.isChecked = selectedAll
            }
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
        initData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        setHasOptionsMenu(false)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main_fragment_book_source_menu, menu)
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
                        toast("导入")
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
//            R.id.sync_book_script -> {
//                //同步书源。退出后就会停止同步。用actor会更好一点。
//                launch {
//                    showSnackbar(recycle_view, "正在同步书源，请勿退出……", Snackbar.LENGTH_INDEFINITE)
//                    try {
//                        JsUpdateManager.checkRemoteJsUpdate()
//                        showSnackbar(recycle_view, "同步成功", Snackbar.LENGTH_SHORT)
//                        initData()
//                    } catch (throwable: Throwable) {
//                        Log.i("小说脚本同步失败", throwable)
//                        showSnackbar(recycle_view, "同步失败", Snackbar.LENGTH_SHORT)
//                    }
//                }
//
//                true
//            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initData() {
        launch {
            val allJs = JsManager.getAllJs()
            adapter.data.clear()
            adapter.data.addAll(allJs)
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
    }

    private fun showPopupMenu(view: View) {
        val popupMenu = PopupMenu(view.context, view)
        popupMenu.menuInflater.inflate(R.menu.main_fragment_book_source_menu_bottom, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener {
            toast("${it.title}")
            return@setOnMenuItemClickListener false
        }
        popupMenu.show()
    }


    class Adapter(val fragment: BookScriptManagerFragment) : BaseAdapter<JavaScript>() {
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
                tv_source_name.text = "${script.source} V-${script.version} ${script.priority}"
                cb_book_source.setOnCheckedChangeListener(null)
                cb_book_source.isChecked = script.selected
                cb_book_source.setOnCheckedChangeListener { _, b ->
                    script.selected = b
                    onSelectSource()
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
                        JsManager.saveJs(script)
                        showSnackbar(view, if (isChecked) "已启用" else "已禁用")
                    }
                }
            }
        }

        override fun getItemId(position: Int): Long {
            return data[position].id
        }
    }
}