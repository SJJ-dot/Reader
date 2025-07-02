package com.sjianjun.reader.module.bookcity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.databinding.FragmentBookCityHomeItemBinding
import com.sjianjun.reader.databinding.FragmentBookCityPageBinding
import com.sjianjun.reader.databinding.FragmentBookCityPageHostItemBinding
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.gone
import com.sjianjun.reader.view.CustomWebView
import com.sjianjun.reader.view.click
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log

private const val ARG_URL = "ARG_URL"

class BookCityPageFragment : BaseFragment() {
    private val adBlockMap = mutableMapOf<String, AdBlock>()
    private var url: String? = null
    private val adBlock: AdBlock get() = adBlockMap.getOrPut(url?.toHttpUrlOrNull()?.topPrivateDomain()?:"") { AdBlock(url) }

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

            drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
                var first = true
                override fun onDrawerOpened(drawerView: View) {
                    if (first) {
                        first = false
                        initDrawer(drawerLayout, customWebView, this@apply)
                    }
                }
            })

            setOnBackPressed {
                if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                    drawerLayout.closeDrawer(GravityCompat.END)
                    true
                } else if (customWebView.onBackPressed()) {
                    true
                } else {
                    false
                }
            }

        }
    }

    private fun initDrawer(drawer: DrawerLayout, view: CustomWebView, binding: FragmentBookCityPageBinding) {
        val hostListAdapter = HostListAdapter(adBlock)
        val whiteListAdapter = WhiteListAdapter(adBlock)
        val blackListAdapter = BlackListAdapter(adBlock)
        val homeAdapter = HomeListAdapter {
            url = it
            view.loadUrl(it, true)
            drawer.closeDrawer(GravityCompat.END)
            view.adBlock = adBlock
            initAdBlockList( hostListAdapter, whiteListAdapter, blackListAdapter)
        }
        homeAdapter.data.addAll(globalConfig.bookCityUrlHistoryList)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = homeAdapter
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                Log.i("tab:${tab?.position}")
                binding.recyclerView.adapter = when (tab?.position) {
                    0 -> homeAdapter
                    1 -> hostListAdapter
                    2 -> whiteListAdapter
                    3 -> blackListAdapter
                    else -> hostListAdapter
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }
        })
        initAdBlockList(hostListAdapter, whiteListAdapter, blackListAdapter)
    }

    private fun initAdBlockList(hostListAdapter: HostListAdapter,
                                whiteListAdapter: WhiteListAdapter,
                                blackListAdapter: BlackListAdapter) {
        hostListAdapter.adBlock.hostList.removeObservers(viewLifecycleOwner)
        hostListAdapter.adBlock.whitelist.removeObservers(viewLifecycleOwner)
        hostListAdapter.adBlock.blacklist.removeObservers(viewLifecycleOwner)

        hostListAdapter.adBlock = adBlock
        whiteListAdapter.adBlock = adBlock
        blackListAdapter.adBlock = adBlock
        adBlock.hostList.observe(viewLifecycleOwner) {
            hostListAdapter.data = it
            hostListAdapter.notifyDataSetChanged()
        }
        adBlock.blacklist.observe(viewLifecycleOwner) {
            blackListAdapter.data = it
            blackListAdapter.notifyDataSetChanged()
        }
        adBlock.whitelist.observe(viewLifecycleOwner) {
            whiteListAdapter.data = it
            whiteListAdapter.notifyDataSetChanged()
        }
    }

    class HomeListAdapter(val onClick: (url: String) -> Unit) : BaseAdapter<String>(R.layout.fragment_book_city_home_item) {

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val binding = FragmentBookCityHomeItemBinding.bind(holder.itemView)
            binding.tvHost.text = data[position]
            binding.root.click {
                onClick(data[position])
            }
        }
    }

    class HostListAdapter(var adBlock: AdBlock) : BaseAdapter<HostStr>(R.layout.fragment_book_city_page_host_item) {
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val binding = FragmentBookCityPageHostItemBinding.bind(holder.itemView)
            binding.tvHost.text = data[position].host
            binding.tvTime.text = data[position].time
            binding.btnMarkWhite.text = "+白名单"
            binding.btnMarkWhite.click {
                adBlock.addWhiteHost(data[position])
            }
            binding.btnMarkBlack.text = "+黑名单"
            binding.btnMarkBlack.click {
                adBlock.addBlackHost(data[position])
            }
        }
    }

    class WhiteListAdapter(var adBlock: AdBlock) : BaseAdapter<HostStr>(R.layout.fragment_book_city_page_host_item) {
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val binding = FragmentBookCityPageHostItemBinding.bind(holder.itemView)
            binding.tvHost.text = data[position].host
            binding.tvTime.text = data[position].time
            binding.btnMarkBlack.gone()
            binding.btnMarkWhite.text = "移除白名单"
            binding.btnMarkWhite.click {
                adBlock.removeWhiteHost(data[position])
            }
        }

    }

    class BlackListAdapter(var adBlock: AdBlock) : BaseAdapter<HostStr>(R.layout.fragment_book_city_page_host_item) {
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val binding = FragmentBookCityPageHostItemBinding.bind(holder.itemView)
            binding.tvHost.text = data[position].host
            binding.tvTime.text = data[position].time
            binding.btnMarkWhite.gone()
            binding.btnMarkBlack.text = "移除黑名单"
            binding.btnMarkBlack.click {
                adBlock.removeBlackHost(data[position])
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