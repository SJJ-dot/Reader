package com.sjianjun.reader.module.bookcity

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.databinding.FragmentBookCityHomeItemBinding
import com.sjianjun.reader.databinding.FragmentBookCityPageBinding
import com.sjianjun.reader.databinding.FragmentBookCityPageHistoryItemBinding
import com.sjianjun.reader.databinding.FragmentBookCityPageHostItemBinding
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.color
import com.sjianjun.reader.utils.colorText
import com.sjianjun.reader.utils.gone
import com.sjianjun.reader.utils.htmlToSpanned
import com.sjianjun.reader.utils.show
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.CustomWebView
import com.sjianjun.reader.view.click
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log
import kotlin.getValue

private const val ARG_URL = "ARG_URL"

class BookCityPageFragment : BaseFragment() {
    private val vm by viewModels<BookCityViewModel>()
    private var url: String? = null
    private val adBlock: AdBlock = AdBlock()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_book_city_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        FragmentBookCityPageBinding.bind(view).apply {
            url = globalConfig.bookCityUrl
            customWebView.init(viewLifecycleOwner, adBlock)
            customWebView.openMenu = {
                drawerLayout.openDrawer(GravityCompat.END)
            }
            //QQ登录
            globalConfig.qqAuthLoginUri.observe(viewLifecycleOwner, Observer {
                val url = it?.toString() ?: return@Observer
                customWebView.loadUrl(url, true)
            })
            customWebView.loadUrl(url!!, true)

            initDrawer(drawerLayout, customWebView, this@apply)
            var lastTime = System.currentTimeMillis()
            setOnBackPressed {
                if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                    drawerLayout.closeDrawer(GravityCompat.END)
                    true
                } else {
                    if (!customWebView.back()) {
                        if (System.currentTimeMillis() - lastTime > 1000) {
                            toast("双击退出")
                            lastTime = System.currentTimeMillis()
                            true
                        } else {
                            false
                        }
                    } else {
                        true
                    }

                }
            }

        }
    }

    private fun initHomeListAdapterData(homeAdapter: HomeListAdapter) {
        lifecycleScope.launch {
            val sites = vm.getAllBookSourceSite().distinct().toMutableList()
            val list = globalConfig.bookCityUrlHistoryList.toMutableList()
            homeAdapter.data.clear()
            homeAdapter.data.addAll(list.map { it to false })
            homeAdapter.data.addAll(sites.map { it to true })
            homeAdapter.notifyDataSetChanged()
        }
    }

    private fun initDrawer(drawer: DrawerLayout, view: CustomWebView, binding: FragmentBookCityPageBinding) {
        binding.btnCopyUrl.click {
            view.copyUrlToClipboard()
        }
        binding.btnCopyTitle.click {
            view.copyTitleToClipboard()
        }
        val historyAdapter = HistoryListAdapter()
        historyAdapter.onClick = {
            view.loadUrl(it, false)
        }
        historyAdapter.onDeleteHistory = { item ->
            if (item == null) {
                view.clearHistory()
            } else {
                view.removeHistory(item)
            }
        }
        view.history.observe(viewLifecycleOwner) {
            historyAdapter.data = it.toMutableList()
            historyAdapter.notifyDataSetChanged()
        }
        val hostListAdapter = HostListAdapter(adBlock)
        val blackListAdapter = BlackListAdapter(adBlock)
        val homeAdapter = HomeListAdapter {
            url = it
            view.loadUrl(it, true)
            drawer.closeDrawer(GravityCompat.END)
            view.adBlock = adBlock
            initAdBlockList(hostListAdapter, blackListAdapter)
        }
        homeAdapter.onLongClickListener = {
            AlertDialog.Builder(requireActivity())
                .setTitle("删除")
                .setMessage("确定删除吗？\n${it.first}")
                .setPositiveButton("删除") { _, _ ->
                    val list = globalConfig.bookCityUrlHistoryList.toMutableList()
                    list.remove(it.first)
                    globalConfig.bookCityUrlHistoryList = list
                    initHomeListAdapterData(homeAdapter)
                }
                .setNegativeButton("取消") { _, _ -> }
                .show()
        }
        initHomeListAdapterData(homeAdapter)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = homeAdapter
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                Log.i("tab:${tab?.position}")
                binding.recyclerView.adapter = when (tab?.position) {
                    0 -> homeAdapter
                    1 -> hostListAdapter
                    2 -> blackListAdapter
                    3 -> historyAdapter
                    else -> hostListAdapter
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }
        })
        initAdBlockList(hostListAdapter, blackListAdapter)
    }

    private fun initAdBlockList(
        hostListAdapter: HostListAdapter,
        blackListAdapter: BlackListAdapter
    ) {
        hostListAdapter.adBlock.hostList.removeObservers(viewLifecycleOwner)
        hostListAdapter.adBlock.blacklist.removeObservers(viewLifecycleOwner)

        hostListAdapter.adBlock = adBlock
        blackListAdapter.adBlock = adBlock
        adBlock.hostList.observe(viewLifecycleOwner) {
            hostListAdapter.data = it
            hostListAdapter.notifyDataSetChanged()
        }
        adBlock.blacklist.observe(viewLifecycleOwner) {
            blackListAdapter.data = it
            blackListAdapter.notifyDataSetChanged()
        }
    }

    class HomeListAdapter(val onClick: (url: String) -> Unit) : BaseAdapter<Pair<String, Boolean>>(R.layout.fragment_book_city_home_item) {
        var onLongClickListener: (Pair<String, Boolean>) -> Unit = {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val binding = FragmentBookCityHomeItemBinding.bind(holder.itemView)
            binding.tvHost.text = data[position].first
            if (data[position].second) {
                binding.tvHostFlag.show()
            } else {
                binding.tvHostFlag.gone()
            }
            binding.root.click {
                onClick(data[position].first)
            }
            binding.root.setOnLongClickListener {
                onLongClickListener(data[position])
                true
            }
        }
    }

    class HostListAdapter(var adBlock: AdBlock) : BaseAdapter<HostStr>(R.layout.fragment_book_city_page_host_item) {
        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val binding = FragmentBookCityPageHostItemBinding.bind(holder.itemView)
            val host = data[position]
            val type = host.type.joinToString(",", transform = { colorText(it, R.color.colorPrimary.color(holder.itemView.context)) })
            binding.tvHost.text = (host.host + "<br/>" + type).htmlToSpanned()
            binding.tvTime.text = host.time
            binding.btnMarkBlack.text = "+黑名单"
            binding.btnMarkBlack.click {
                adBlock.addBlackHost(host)
            }
        }
    }

    class BlackListAdapter(var adBlock: AdBlock) : BaseAdapter<HostStr>(R.layout.fragment_book_city_page_host_item) {
        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val binding = FragmentBookCityPageHostItemBinding.bind(holder.itemView)
            val host = data[position]
            val type = host.type.joinToString(",", transform = { colorText(it, R.color.colorPrimary.color(holder.itemView.context)) })
            binding.tvHost.text = (host.host + "<br/>" + type).htmlToSpanned()
            binding.tvTime.text = host.time
            binding.btnMarkBlack.text = "-移除"
            binding.btnMarkBlack.click {
                adBlock.removeBlackHost(data[position])
            }
        }
    }

    class HistoryListAdapter : BaseAdapter<CustomWebView.HistoryItem>(R.layout.fragment_book_city_page_history_item) {
        var onClick: ((url: String) -> Unit)? = null
        var onDeleteHistory: ((item: CustomWebView.HistoryItem?) -> Unit)? = null
        private val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val binding = FragmentBookCityPageHistoryItemBinding.bind(holder.itemView)
            val history = data[position]
            binding.tvTitle.text = history.title
            binding.tvTime.text = sdf.format(history.time)
            binding.root.click {
                onClick?.invoke(history.url)
            }
            binding.root.setOnLongClickListener {
                AlertDialog.Builder(it.context).apply {
                    setTitle("删除记录")
                    setMessage("是否删除 ${history.title} ?")
                    setNegativeButton("取消", null)
                    setPositiveButton("删除") { _, _ ->
                        onDeleteHistory?.invoke(history)
                    }
                    setNeutralButton("清空") { _, _ ->
                        AlertDialog.Builder(it.context).apply {
                            setTitle("清空全部记录")
                            setMessage("是否清空全部历史记录 ?")
                            setNegativeButton("取消", null)
                            setPositiveButton("清空") { _, _ ->
                                onDeleteHistory?.invoke(null)
                            }.show()
                        }
                    }
                }.show()
                return@setOnLongClickListener true
            }
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(url: String) =
            BookCityPageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, url)
                }
            }
    }
}