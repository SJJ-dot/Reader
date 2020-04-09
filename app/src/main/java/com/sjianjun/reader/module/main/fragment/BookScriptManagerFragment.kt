package com.sjianjun.reader.module.main.fragment

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.startActivity
import kotlinx.android.synthetic.main.main_fragment_book_script_manager.*

class BookScriptManagerFragment : BaseFragment() {
    override fun getLayoutRes() = R.layout.main_fragment_book_script_manager
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main_fragment_book_source_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.import_book_script -> {
//                startActivity<>()
                true
            }
            R.id.sync_book_script -> {
                //同步书源。退出后就会停止同步。用actor会更好一点。
                viewLaunch {
                    newSnackbar(recycle_view, "正在同步书源，请勿退出……", Snackbar.LENGTH_INDEFINITE)
                    DataManager.reloadBookJavaScript()
                    newSnackbar(recycle_view, "同步成功", Snackbar.LENGTH_SHORT)
                }

                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}