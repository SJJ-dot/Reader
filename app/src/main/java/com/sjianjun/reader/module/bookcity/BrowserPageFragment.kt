package com.sjianjun.reader.module.bookcity

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.CROSS_ORIGIN_MODE
import com.sjianjun.reader.R
import com.sjianjun.reader.URL
import com.sjianjun.reader.WEB_ID
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.WebBook
import com.sjianjun.reader.databinding.FragmentBookCityPageBinding
import com.sjianjun.reader.databinding.FragmentBookCityPageHistoryItemBinding
import com.sjianjun.reader.databinding.FragmentBookCityPageHostItemBinding
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.color
import com.sjianjun.reader.utils.gone
import com.sjianjun.reader.utils.setTextColorRes
import com.sjianjun.reader.utils.show
import com.sjianjun.reader.view.click
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import sjj.alog.Log

/** 把拦截范围 mode 转成中文标签（文件级私有，供同文件内的适配器使用） */
private fun modeLabel(mode: String): String = when (mode) {
    BLOCK_MODE_DOMAIN -> "顶级域名"
    BLOCK_MODE_SUBDOMAIN -> "子域名"
    BLOCK_MODE_URL -> "精确URL"
    else -> "顶级域名"
}

class BrowserPageFragment : BaseFragment() {
    private val vm by viewModels<BookCityViewModel>()
    private var url: String? = null
    private val adBlock: AdBlock = AdBlock
    private var webBook: WebBook? = null
    private var crossOriginMode: Int = -1
    private var pageBinding: FragmentBookCityPageBinding? = null

    /** 夜间模式切换导致重建时，用来恢复 WebView 的当前页/历史 */
    private var savedWebState: Bundle? = null

    /** init() 是否已完成：未完成时 onStart 不能触发 onForeground，避免与初始化抢建 WebView */
    private var initialized = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_book_city_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val bind = FragmentBookCityPageBinding.bind(view)
        pageBinding = bind
        // 重建（夜间模式切换等）时优先恢复当前页，而不是回到首页
        savedWebState = savedInstanceState?.getBundle(KEY_WEB_STATE)
        lifecycleScope.launch {
            init(bind)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val webView = pageBinding?.customWebView?.webView ?: return
        if (webView.isDestroyed) return
        runCatching {
            val state = Bundle()
            webView.saveState(state)
            outState.putBundle(KEY_WEB_STATE, state)
        }.onFailure { Log.e("保存 WebView 状态失败", it) }
    }

    internal fun onAppBackground() {
        pageBinding?.customWebView?.onBackground()
    }

    override fun onStart() {
        super.onStart()
        // init() 是挂起函数（要查 webId），未完成时不能触发 onForeground，
        // 否则两边会抢着创建 WebView，夜间模式重建后容易出现空白页
        if (initialized) pageBinding?.customWebView?.onForeground()
    }

    override fun onDestroyView() {
        // 离开书城页（切到其它抽屉菜单 / 宿主销毁）时释放 WebView：
        // 否则复用的 WebView 仍挂在已销毁的视图树上，下次进入会因“已有父容器”而无法重新挂载
        pageBinding?.customWebView?.onBackground()
        initialized = false
        pageBinding = null
        super.onDestroyView()
    }

    private suspend fun init(bind: FragmentBookCityPageBinding) {
        bind.apply {
            val webId = arguments?.getString(WEB_ID)
            if (webId?.isNotBlank() == true) {
                webBook = vm.getWebBookById(webId).firstOrNull()
                url = webBook?.lastUrl ?: webBook?.url
            }
            if (url == null) {
                url = arguments?.getString(URL) ?: globalConfig.bookCityUrl
            }
            // 有恢复的标签时，加载当前标签页面（仅书城主入口恢复标签；
            // 字典/搜索等外部打开指定 URL 时不做恢复，避免覆盖传入的 URL）
            if (arguments?.getBoolean(KEY_RESTORE_TABS, false) == true) {
                // 进程重启后先从持久化恢复标签元数据，否则标签列表会从空开始（表现为“标签被清空”）
                BrowserTabs.restore()
                BrowserTabs.tabs.getOrNull(BrowserTabs.activeIndex)?.url?.takeIf { it.isNotBlank() }
                    ?.let { url = it }
            }
            crossOriginMode = arguments?.getInt(CROSS_ORIGIN_MODE) ?: -1

            customWebView.crossOriginOverride = crossOriginMode
            customWebView.init(viewLifecycleOwner, adBlock, webBook)
            // 菜单「退出」：独立浏览器里结束 Activity；内嵌在首页时只回到书架，避免退出整个 App
            customWebView.onExitRequest = {
                val act = requireActivity()
                if (act is WebViewBrowserActivity) {
                    act.finish()
                } else if (!findNavController().popBackStack(R.id.bookShelfFragment, false)) {
                    act.onBackPressedDispatcher.onBackPressed()
                }
            }
            // 有保存状态就恢复到当前页（夜间模式切换不跳回首页），否则正常加载
            val state = savedWebState
            savedWebState = null
            if (state == null || !customWebView.restoreState(state)) {
                customWebView.loadUrl(url!!)
            }

            EventBus.observe(EventKey.OPEN_BROWSER_URL, viewLifecycleOwner, Observer<String> {
                if (it.isNotBlank()) customWebView.loadUrl(it)
            })
            EventBus.observe(EventKey.OPEN_BROWSER_URL_NEW_TAB, viewLifecycleOwner, Observer<String> {
                if (it.isNotBlank()) customWebView.openInNewTab(it)
            })
            val binding = this
            setOnBackPressed {
                val rootInsets = ViewCompat.getRootWindowInsets(binding.root)
                val imeVisible = rootInsets?.isVisible(WindowInsetsCompat.Type.ime()) == true
                if (imeVisible) {
                    WindowInsetsControllerCompat(requireActivity().window, binding.root).hide(WindowInsetsCompat.Type.ime())
                    return@setOnBackPressed true
                }
                if (!customWebView.back()) {
                    false
                } else {
                    true
                }
            }
        }
        initialized = true
    }

    class BlackListAdapter(var adBlock: AdBlock) : BaseAdapter<HostStr>(R.layout.fragment_book_city_page_host_item) {
        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val binding = FragmentBookCityPageHostItemBinding.bind(holder.itemView)
            val host = data[position]
            if (host.isPage) {
                binding.root.setCardBackgroundColor(R.color.colorPrimary.color(holder.itemView.context))
                binding.tvHost.setTextColorRes(R.color.mdr_white)
                binding.tvType.setTextColorRes(R.color.mdr_white)
                binding.tvTime.setTextColorRes(R.color.mdr_white)
                binding.btnMarkBlack.setTextColorRes(R.color.colorPrimary)
                binding.btnMarkBlack.setSolid(R.color.mdr_white.color(holder.itemView.context))
            } else {
                binding.root.setCardBackgroundColor(R.color.dn_bookcity_host_item_background.color(holder.itemView.context))
                binding.tvHost.setTextColorRes(R.color.dn_text_color_black)
                binding.tvType.setTextColorRes(R.color.colorPrimary)
                binding.tvTime.setTextColorRes(R.color.dn_text_color_black)
                binding.btnMarkBlack.setTextColorRes(R.color.mdr_white)
                binding.btnMarkBlack.setSolid(R.color.colorPrimary.color(holder.itemView.context))
            }
            binding.tvType.text = "${modeLabel(host.mode)} · 命中 ${adBlock.getHitCount(host.host, host.mode)} 次"
            binding.tvHost.text = host.host
            binding.tvTime.text = host.time
            binding.btnMarkBlack.text = "-移除"
            binding.btnMarkBlack.click {
                adBlock.removeBlackHost(data[position])
            }
        }
    }

    class WhiteListAdapter(var adBlock: AdBlock) : BaseAdapter<HostStr>(R.layout.fragment_book_city_page_host_item) {
        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val binding = FragmentBookCityPageHostItemBinding.bind(holder.itemView)
            val host = data[position]
            if (host.isPage) {
                binding.root.setCardBackgroundColor(R.color.colorPrimary.color(holder.itemView.context))
                binding.tvHost.setTextColorRes(R.color.mdr_white)
                binding.tvType.setTextColorRes(R.color.mdr_white)
                binding.tvTime.setTextColorRes(R.color.mdr_white)
                binding.btnMarkBlack.setTextColorRes(R.color.colorPrimary)
                binding.btnMarkBlack.setSolid(R.color.mdr_white.color(holder.itemView.context))
            } else {
                binding.root.setCardBackgroundColor(R.color.dn_bookcity_host_item_background.color(holder.itemView.context))
                binding.tvHost.setTextColorRes(R.color.dn_text_color_black)
                binding.tvType.setTextColorRes(R.color.colorPrimary)
                binding.tvTime.setTextColorRes(R.color.dn_text_color_black)
                binding.btnMarkBlack.setTextColorRes(R.color.mdr_white)
                binding.btnMarkBlack.setSolid(R.color.colorPrimary.color(holder.itemView.context))
            }
            binding.tvType.text = "${modeLabel(host.mode)} · 命中 ${adBlock.getHitCount(host.host, host.mode)} 次"
            binding.tvHost.text = host.host
            binding.tvTime.text = host.time
            binding.btnMarkBlack.text = "-移除"
            binding.btnMarkBlack.click {
                adBlock.removeWhiteHost(data[position])
            }
        }
    }

    class HistoryListAdapter : BaseAdapter<HistoryItem>(R.layout.fragment_book_city_page_history_item) {
        var onClick: ((url: String) -> Unit)? = null
        var onDeleteHistory: ((item: HistoryItem?) -> Unit)? = null
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
                MaterialAlertDialogBuilder(it.context).apply {
                    setTitle("删除记录")
                    setMessage("是否删除 ${history.title} ?")
                    setNegativeButton("取消", null)
                    setPositiveButton("删除") { _, _ ->
                        onDeleteHistory?.invoke(history)
                    }
                    setNeutralButton("清空") { _, _ ->
                        MaterialAlertDialogBuilder(it.context).apply {
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
        /** 重建时恢复 WebView 状态用（当前页/历史） */
        private const val KEY_WEB_STATE = "book_city_web_state"
        /** 是否恢复上次标签列表 */
        private const val KEY_RESTORE_TABS = "restore_tabs"

        @JvmStatic
        fun newInstance(url: String, webId: String = "", crossOriginMode: Int = -1, restoreTabs: Boolean = false) =
            BrowserPageFragment().apply {
                arguments = Bundle().apply {
                    putString(URL, url)
                    putString(WEB_ID, webId)
                    putInt(CROSS_ORIGIN_MODE, crossOriginMode)
                    putBoolean(KEY_RESTORE_TABS, restoreTabs)
                }
            }
    }
}
