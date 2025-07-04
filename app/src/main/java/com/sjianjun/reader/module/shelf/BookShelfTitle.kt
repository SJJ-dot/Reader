package com.sjianjun.reader.module.shelf

import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.navigation.fragment.NavHostFragment
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.BookShelfTitleBinding
import com.sjianjun.reader.utils.color
import com.sjianjun.reader.view.click

class BookShelfTitle() {
    private var activity: AppCompatActivity? = null
    private var titleBinding: BookShelfTitleBinding? = null
    fun destroyTileView() {
        activity?.supportActionBar?.apply {
            setDisplayShowTitleEnabled(true)
            setDisplayShowCustomEnabled(false)
        }
        activity?.findViewById<ConstraintLayout>(R.id.toolbar_content)?.apply {
            removeAllViews()
        }
        titleBinding = null
    }

    fun initTileView(fragment: BaseFragment) {
        activity = fragment.requireActivity() as AppCompatActivity
        activity?.supportActionBar?.apply {
            setDisplayShowTitleEnabled(false)
            setDisplayShowCustomEnabled(true)
        }
        activity?.findViewById<ConstraintLayout>(R.id.toolbar_content)?.apply {
            removeAllViews()
            titleBinding = BookShelfTitleBinding.inflate(activity!!.layoutInflater, this, true)
        }
        val controller = NavHostFragment.findNavController(fragment)
        titleBinding?.tvShelfSource?.click {
            showSourceData(true)
            if (controller.currentDestination?.id != R.id.bookShelfFragment) {
                controller.navigate(R.id.bookShelfFragment)
            }
        }
        titleBinding?.tvShelfWeb?.click {
            showSourceData(false)
            if (controller.currentDestination?.id != R.id.webShelfFragment) {
                controller.navigate(R.id.webShelfFragment)
            }
        }
    }

    fun showSourceData(bool: Boolean) {
        val selectedColor = R.color.mdr_white.color(activity)
        val unselectedColor = R.color.colorPrimaryDark.color(activity)
        titleBinding?.tvShelfSource?.solid = if (bool) selectedColor else unselectedColor
        titleBinding?.tvShelfWeb?.solid = if (!bool) selectedColor else unselectedColor

        val selectedTextColor = R.color.colorPrimary.color(activity)
        val unselectedTextColor = R.color.mdr_white.color(activity)
        titleBinding?.tvShelfSource?.setTextColor(if (bool) selectedTextColor else unselectedTextColor)
        titleBinding?.tvShelfWeb?.setTextColor(if (!bool) selectedTextColor else unselectedTextColor)
    }
}