package com.sjianjun.reader.module.script

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BaseAsyncFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.JAVA_SCRIPT_SOURCE
import com.sjianjun.reader.utils.id
import com.sjianjun.reader.utils.showSnackbar
import com.sjianjun.reader.utils.startActivity
import kotlinx.android.synthetic.main.main_fragment_book_script_manager.*
import kotlinx.android.synthetic.main.script_item_fragment_manager_java_script.view.*
import kotlinx.coroutines.flow.collectLatest

class BookScriptManagerFragment : BaseAsyncFragment() {
    override fun getLayoutRes() = R.layout.main_fragment_book_script_manager

    override val onLoadedView: (View) -> Unit = {
        setHasOptionsMenu(true)
        val adapter = Adapter(this@BookScriptManagerFragment)
        recycle_view.adapter = adapter
        launch {
            DataManager.getAllJavaScript().collectLatest {
                adapter.data.clear()
                adapter.data.addAll(it)
                adapter.notifyDataSetChanged()
            }
        }
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
            R.id.sync_book_script -> {
                //同步书源。退出后就会停止同步。用actor会更好一点。
                launch {
                    showSnackbar(recycle_view, "正在同步书源，请勿退出……", Snackbar.LENGTH_INDEFINITE)
                    try {
                        DataManager.reloadBookJavaScript()
                        showSnackbar(recycle_view, "同步成功", Snackbar.LENGTH_SHORT)
                    } catch (throwable: Throwable) {
                        showSnackbar(recycle_view, "同步失败", Snackbar.LENGTH_SHORT)
                    }
                }

                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    class Adapter(val fragment: BookScriptManagerFragment) : BaseAdapter<JavaScript>() {

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
            val script = data[p1]
            holder.itemView.cb_book_source.text =
                "${script.source} V-${script.version} ${script.priority}"
            holder.itemView.iv_del_source.setOnClickListener {
                fragment.launch {
                    DataManager.deleteJavaScript(script)
                    showSnackbar(it, "删除成功")
                }
            }
            holder.itemView.iv_edit_source.setOnClickListener {
                fragment.startActivity<EditJavaScriptActivity>(JAVA_SCRIPT_SOURCE, script.source)
            }

            holder.itemView.cb_book_source.setOnCheckedChangeListener(null)
            holder.itemView.cb_book_source.isChecked = script.enable

            holder.itemView.cb_book_source.setOnCheckedChangeListener { view, isChecked ->
                fragment.launch {
                    script.enable = isChecked
                    DataManager.updateJavaScript(script)
                    showSnackbar(view, if (isChecked) "已启用" else "已停用")
                }
            }

        }

        override fun getItemId(position: Int): Long {
            return data[position].id
        }
    }
}