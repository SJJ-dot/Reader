package com.sjianjun.reader.module.bookcity

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.ActivityBrowserAdBlockBinding
import kotlinx.coroutines.launch

/** 广告拦截独立列表页：拦截记录 / 黑名单 / 白名单 */
class BrowserAdBlockActivity : BaseActivity() {

    private val binding by lazy { ActivityBrowserAdBlockBinding.inflate(layoutInflater) }
    private val adBlock: AdBlock = AdBlock

    private lateinit var accessAdapter: AccessRecordAdapter
    private lateinit var blackAdapter: BlackListAdapter
    private lateinit var whiteAdapter: WhiteListAdapter

    /** 白名单与书源域名同步：新增加入、删除移除 */
    private fun seedBookSourceWhitelist() {
        lifecycleScope.launch {
            runCatching {
                val hosts = BookCityViewModel().getAllBookSourceSite()
                    .mapNotNull { site ->
                        runCatching { java.net.URI(site.url).host }.getOrNull()
                    }
                    .filter { it.isNotBlank() }
                    .distinct()
                BookSourceWhitelistSync.sync(adBlock, hosts)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        accessAdapter = AccessRecordAdapter(
            onAddWhiteDomain = { d, m -> adBlock.addWhiteHost(d, m) },
            onAddBlackChoice = { url, _ ->
                showBlockScopeDialog(this, url) { v, m -> adBlock.addBlackHost(v, m) }
            },
            onRestore = { url -> adBlock.restoreBlocked(url) }
        )
        blackAdapter = BlackListAdapter(adBlock)
        whiteAdapter = WhiteListAdapter(adBlock)

        binding.rvList.layoutManager = LinearLayoutManager(this)
        binding.rvList.adapter = accessAdapter
        binding.filterScroll.visibility = View.VISIBLE
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                binding.rvList.adapter = when (tab?.position) {
                    1 -> blackAdapter
                    2 -> whiteAdapter
                    else -> accessAdapter
                }
                // 筛选栏仅在访问记录 tab 显示
                binding.filterScroll.visibility = if (tab?.position == 0) View.VISIBLE else View.GONE
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })

        adBlock.accessPages.observe(this, Observer {
            accessAdapter.submit(adBlock.snapshot())
            updateFilterChips()
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

    /** 筛选可选类型（与 AccessRecordAdapter.resourceCategory 的分类一致） */
    private val filterTypes = listOf("图片", "脚本", "样式", "字体", "视频", "音频", "其他")

    private var filterSelection: String? = null

    /** 动态构建筛选按钮（CardView 卡片样式）：仅展示列表中实际存在的类型，"全部"始终显示 */
    private fun updateFilterChips() {
        val active = accessAdapter.activeCategories()
        // 当前选中的类型已不在数据中时，重置回"全部"
        if (filterSelection != null && filterSelection !in active) {
            filterSelection = null
            accessAdapter.setTypeFilter(null)
        }
        val container = binding.filterContainer
        container.removeAllViews()
        val density = resources.displayMetrics.density
        for (cat in listOf<String?>(null) + filterTypes.filter { it in active }) {
            container.addView(buildFilterButton(cat, cat == filterSelection, density))
        }
    }

    private fun buildFilterButton(cat: String?, selected: Boolean, density: Float): View {
        val card = MaterialCardView(this)
        card.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, (26 * density).toInt()
        ).apply { marginEnd = (6 * density).toInt() }
        card.radius = 6 * density
        card.cardElevation = 0f
        card.strokeWidth = (1 * density).toInt()
        card.isClickable = true
        card.isFocusable = true
        if (selected) {
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))
//            card.strokeColor = ContextCompat.getColor(this, R.color.colorPrimary)
        } else {
            card.setCardBackgroundColor(Color.WHITE)
//            card.strokeColor = Color.parseColor("#CFCFCF")
        }
        val label = TextView(this)
        label.text = cat ?: "全部"
        label.textSize = 11f
        label.gravity = Gravity.CENTER
        label.setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
        label.setTextColor(
            if (selected) Color.WHITE
            else ContextCompat.getColor(this, R.color.dn_text_color_black)
        )
        card.addView(
            label, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER
            )
        )
        card.setOnClickListener {
            if (filterSelection != cat) {
                filterSelection = cat
                accessAdapter.setTypeFilter(cat)
                updateFilterChips()
            }
        }
        return card
    }
}
