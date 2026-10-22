package com.sjianjun.reader.module.bookcity

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.databinding.ActivityBrowserAdBlockBinding

/** 广告拦截独立列表页：拦截记录 / 黑名单 / 白名单 */
class BrowserAdBlockActivity : BaseActivity() {

    private val binding by lazy { ActivityBrowserAdBlockBinding.inflate(layoutInflater) }
    private val adBlock: AdBlock = AdBlock

    private lateinit var hostAdapter: BrowserPageFragment.HostListAdapter
    private lateinit var blackAdapter: BrowserPageFragment.BlackListAdapter
    private lateinit var whiteAdapter: BrowserPageFragment.WhiteListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        hostAdapter = BrowserPageFragment.HostListAdapter(adBlock)
        blackAdapter = BrowserPageFragment.BlackListAdapter(adBlock)
        whiteAdapter = BrowserPageFragment.WhiteListAdapter(adBlock)

        binding.rvList.layoutManager = LinearLayoutManager(this)
        binding.rvList.adapter = hostAdapter
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                binding.rvList.adapter = when (tab?.position) {
                    1 -> blackAdapter
                    2 -> whiteAdapter
                    else -> hostAdapter
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })

        adBlock.hostList.observe(this, Observer {
            hostAdapter.data = it
            hostAdapter.notifyDataSetChanged()
        })
        adBlock.blacklist.observe(this, Observer {
            blackAdapter.data = it
            blackAdapter.notifyDataSetChanged()
        })
        adBlock.whitelist.observe(this, Observer {
            whiteAdapter.data = it
            whiteAdapter.notifyDataSetChanged()
        })
    }
}
