package com.sjianjun.reader.module.main.fragment

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.module.script.EditJavaScriptActivity
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.JAVA_SCRIPT_SOURCE
import com.sjianjun.reader.utils.id
import com.sjianjun.reader.utils.startActivity
import com.sjianjun.reader.utils.toastSHORT
import kotlinx.android.synthetic.main.main_fragment_book_script_manager.*
import kotlinx.android.synthetic.main.script_item_fragment_manager_java_script.view.*
import kotlinx.coroutines.flow.collectLatest

class BookScriptManagerFragment : BaseFragment() {
    override fun getLayoutRes() = R.layout.main_fragment_book_script_manager
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        base_url.setText(globalConfig.javaScriptBaseUrl)
        save_base_url.setOnClickListener {
            globalConfig.javaScriptBaseUrl = base_url.text.toString()
        }
        val adapter = Adapter(this)
        recycle_view.adapter = adapter
        viewLaunch {
            DataManager.getAllJavaScript().collectLatest {
                adapter.data = it
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main_fragment_book_source_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.sync_book_script -> {
                //同步书源。退出后就会停止同步。用actor会更好一点。
                viewLaunch {
                    newSnackbar(recycle_view, "正在同步书源，请勿退出……", Snackbar.LENGTH_INDEFINITE)
                    try {
                        DataManager.reloadBookJavaScript()
                        newSnackbar(recycle_view, "同步成功", Snackbar.LENGTH_SHORT)
                    } catch (e: Throwable) {
                        newSnackbar(recycle_view, "同步失败:${e.message}", Snackbar.LENGTH_SHORT)
                    }
                }

                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    class Adapter(val fragment: BookScriptManagerFragment) : BaseAdapter() {

        init {
            setHasStableIds(true)
        }

        var data: List<JavaScript> = listOf()

        override fun itemLayoutRes(viewType: Int): Int {
            return R.layout.script_item_fragment_manager_java_script
        }

        override fun getItemCount(): Int = data.size

        override fun onBindViewHolder(
            holder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
            p1: Int
        ) {
            val script = data[p1]
            holder.itemView.cb_book_source.text = script.source
            holder.itemView.iv_del_source.setOnClickListener {
                fragment.viewLaunch {
                    DataManager.deleteJavaScript(script)
                    fragment.newSnackbar(it, "删除成功")
                }
            }
            holder.itemView.iv_edit_source.setOnClickListener {
                fragment.startActivity<EditJavaScriptActivity>(JAVA_SCRIPT_SOURCE, script.source)
            }

            holder.itemView.cb_book_source.setOnCheckedChangeListener(null)
            holder.itemView.cb_book_source.isChecked = script.enable

            holder.itemView.cb_book_source.setOnCheckedChangeListener { view, isChecked ->
                fragment.viewLaunch {
                    script.enable = isChecked
                    DataManager.updateJavaScript(script)
                    fragment.newSnackbar(view, if (isChecked) "已启用" else "已停用")
                }
            }

        }

        override fun getItemId(position: Int): Long {
            return data[position].source.id
        }
    }
}