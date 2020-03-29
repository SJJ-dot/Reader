package com.sjianjun.reader.module.main.fragment

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.navigation.fragment.NavHostFragment
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import sjj.alog.Log

class BookshelfFragment : BaseFragment() {
    override fun getLayoutRes() = R.layout.main_fragment_book_shelf
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main_fragment_book_shelf_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.search_book_shelf -> {
                NavHostFragment.findNavController(this).navigate(R.id.searchFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}