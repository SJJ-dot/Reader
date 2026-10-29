package com.sjianjun.reader.module.shelf

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.FragmentBookShelfHostBinding
import com.sjianjun.reader.view.click
import sjj.alog.Log

/**
 * 书架页容器：用 ViewPager2 把「书架」与「网页」两个页面合并，支持左右滑动切换。
 * 顶部切换器（书架/网页）注入到 MainActivity 的 toolbar_content，点击或滑动都会联动。
 */
class BookShelfHostFragment : Fragment() {

    private var binding: FragmentBookShelfHostBinding? = null
    private var tabView: View? = null
    private var tvShelfSource: TextView? = null
    private var tvShelfWeb: TextView? = null
    private var navController: NavController? = null

    /** NavigationUI 会在目的地切换时设置标题，这里在其之后（注册更晚）再次隐藏，保持原样式无标题 */
    private val titleHider = NavController.OnDestinationChangedListener { _, destination, _ ->
        Log.i("▶ShelfTitle onDestinationChanged id=${destination.id} label=${destination.label}")
        if (destination.id == R.id.bookShelfFragment) {
            hideTitle("onDestinationChanged")
        }
    }

    /**
     * 隐藏 ActionBar 标题。注意：setDisplayShowTitleEnabled(false) 只能隐藏已存在的标题控件，
     * 若在 NavigationUI 首次 setTitle 之前调用，之后创建的标题控件仍会显示，
     * 因此必须在 setTitle 之后调用（view.post 或 destination 回调中）。
     */
    private fun hideTitle(from: String) {
        val actionBar = (activity as? AppCompatActivity)?.supportActionBar
        Log.i("▶ShelfTitle hideTitle from=$from actionBar=$actionBar")
        actionBar?.setDisplayShowTitleEnabled(false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBookShelfHostBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewPager = binding?.viewPager ?: return
        viewPager.adapter = ShelfPagerAdapter(this)
        setupToolbarTabs(viewPager)
        navController = findNavController()
        navController?.addOnDestinationChangedListener(titleHider)
        // NavigationUI 首次 setTitle 可能在本回调之后（异步 dispatch），post 保证在其之后隐藏
        view.post {
            Log.i("▶ShelfTitle view.post hideTitle 执行")
            hideTitle("view.post")
        }
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                showSourceData(position == 0)
            }
        })
        showSourceData(true)
    }

    private fun setupToolbarTabs(viewPager: ViewPager2) {
        val activity = requireActivity() as AppCompatActivity
        val toolbarContent = activity.findViewById<ViewGroup>(R.id.toolbar_content)
        hideTitle("setupToolbarTabs")
        val v = layoutInflater.inflate(R.layout.view_shelf_tab, toolbarContent, false)
        toolbarContent.addView(v)
        tabView = v
        tvShelfSource = v.findViewById(R.id.tvShelfSource)
        tvShelfWeb = v.findViewById(R.id.tvShelfWeb)
        tvShelfSource?.click { viewPager.currentItem = 0 }
        tvShelfWeb?.click { viewPager.currentItem = 1 }
    }

    /** 高亮当前选中的分段：整体圆角 pill，选中块仅外缘圆角、中间接缝直角 */
    private fun showSourceData(isBookShelf: Boolean) {
        tvShelfSource?.setBackgroundResource(if (isBookShelf) R.drawable.shelf_tab_selected_start else android.R.color.transparent)
        tvShelfWeb?.setBackgroundResource(if (!isBookShelf) R.drawable.shelf_tab_selected_end else android.R.color.transparent)
        val context = requireContext()
        val selectedText = ContextCompat.getColor(context, R.color.colorPrimary)
        val unselectedText = ContextCompat.getColor(context, R.color.mdr_white)
        tvShelfSource?.setTextColor(if (isBookShelf) selectedText else unselectedText)
        tvShelfWeb?.setTextColor(if (!isBookShelf) selectedText else unselectedText)
    }

    override fun onDestroyView() {
        val activity = requireActivity() as? AppCompatActivity
        val toolbarContent = activity?.findViewById<ViewGroup>(R.id.toolbar_content)
        tabView?.let { toolbarContent?.removeView(it) }
        tabView = null
        tvShelfSource = null
        tvShelfWeb = null
        navController?.removeOnDestinationChangedListener(titleHider)
        navController = null
        activity?.supportActionBar?.setDisplayShowTitleEnabled(true)
        binding = null
        super.onDestroyView()
    }

    private class ShelfPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount() = 2

        override fun createFragment(position: Int): Fragment {
            return if (position == 0) BookshelfFragment() else WebShelfFragment()
        }
    }
}
