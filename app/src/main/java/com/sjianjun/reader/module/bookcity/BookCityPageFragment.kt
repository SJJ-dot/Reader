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
import com.sjianjun.reader.databinding.FragmentBookCityPageBinding
import com.sjianjun.reader.databinding.FragmentBookCityPageHostItemBinding
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.gone
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log

private const val ARG_URL = "ARG_URL"

class BookCityPageFragment : BaseFragment() {
    private val url: String get() = globalConfig.bookCityUrl
    private val hostMgr by lazy { HostMgr("BookCity-" + url.toHttpUrlOrNull()?.topPrivateDomain()) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_book_city_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        FragmentBookCityPageBinding.bind(view).apply {
            customWebView.init(viewLifecycleOwner,hostMgr)
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
                        initDrawer(this@apply)
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

    private fun initDrawer(binding: FragmentBookCityPageBinding) {
        val hostListAdapter = HostListAdapter(hostMgr)
        val whiteListAdapter = WhiteListAdapter(hostMgr)
        val blackListAdapter = BlackListAdapter(hostMgr)

        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = hostListAdapter
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                Log.i("tab:${tab?.position}")
                binding.recyclerView.adapter = when (tab?.position) {
                    0 -> hostListAdapter
                    1 -> whiteListAdapter
                    2 -> blackListAdapter
                    else -> hostListAdapter
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }
        })
        hostMgr.hostList.observe(viewLifecycleOwner) {
            hostListAdapter.data = it
            hostListAdapter.notifyDataSetChanged()
        }
        hostMgr.blacklist.observe(viewLifecycleOwner) {
            blackListAdapter.data = it
            blackListAdapter.notifyDataSetChanged()
        }
        hostMgr.whitelist.observe(viewLifecycleOwner) {
            whiteListAdapter.data = it
            whiteListAdapter.notifyDataSetChanged()
        }
    }

    class HostListAdapter(private val hostMgr: HostMgr) : BaseAdapter<HostStr>(R.layout.fragment_book_city_page_host_item) {
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val binding = FragmentBookCityPageHostItemBinding.bind(holder.itemView)
            binding.tvHost.text = data[position].host
            binding.tvTime.text = data[position].time
            binding.btnMarkWhite.text = "+白名单"
            binding.btnMarkWhite.setOnClickListener {
                hostMgr.addWhiteHost(data[position])
            }
            binding.btnMarkBlack.text = "+黑名单"
            binding.btnMarkBlack.setOnClickListener {
                hostMgr.addBlackHost(data[position])
            }
        }
    }

    class WhiteListAdapter(private val hostMgr: HostMgr) : BaseAdapter<HostStr>(R.layout.fragment_book_city_page_host_item) {
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val binding = FragmentBookCityPageHostItemBinding.bind(holder.itemView)
            binding.tvHost.text = data[position].host
            binding.tvTime.text = data[position].time
            binding.btnMarkBlack.gone()
            binding.btnMarkWhite.text = "移除白名单"
            binding.btnMarkWhite.setOnClickListener {
                hostMgr.removeWhiteHost(data[position])
            }
        }

    }

    class BlackListAdapter(private val hostMgr: HostMgr) : BaseAdapter<HostStr>(R.layout.fragment_book_city_page_host_item) {
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val binding = FragmentBookCityPageHostItemBinding.bind(holder.itemView)
            binding.tvHost.text = data[position].host
            binding.tvTime.text = data[position].time
            binding.btnMarkWhite.gone()
            binding.btnMarkBlack.text = "移除黑名单"
            binding.btnMarkBlack.setOnClickListener {
                hostMgr.removeBlackHost(data[position])
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