package com.sjianjun.reader.module.bookcity

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.content.res.Configuration
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.sjianjun.reader.R
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.CROSS_ORIGIN_MODE
import com.sjianjun.reader.URL
import com.sjianjun.reader.databinding.ActivityBrowserReaderBinding
import sjj.alog.Log

class WebViewBrowserActivity : BaseActivity() {
    private val url by lazy { intent.getStringExtra(URL) ?: "" }
    private val crossOriginMode by lazy { intent.getIntExtra(CROSS_ORIGIN_MODE, -1) }
    /** 是否恢复上次的标签列表：仅书城主入口为 true，字典/搜索等外部打开为 false */
    private val restoreTabs by lazy { intent.getBooleanExtra(RESTORE_TABS, false) }
    private val binding by lazy { ActivityBrowserReaderBinding.inflate(layoutInflater) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (url.isBlank()) {
            Log.e("URL is empty, finish activity")
            finish()
            return
        }
        enableEdgeToEdge()
        // 导航栏与底部工具栏同色，避免出现一条不同颜色的底边
        window.navigationBarColor = ContextCompat.getColor(this, R.color.dn_background_card)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightNavigationBars = !night
        // Android 15+ 会强制透明导航栏，window.navigationBarColor 不生效；
        // 用窗口/根布局背景兜底，保证导航栏区域和底部工具栏同色
        val barColor = ContextCompat.getColor(this, R.color.dn_background_card)
        window.setBackgroundDrawable(ColorDrawable(barColor))
        setContentView(binding.root)
        binding.root.setBackgroundColor(barColor)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // 重建（夜间模式切换等）时交给 FragmentManager 恢复原页面，
        // 否则这里会重新 new 一个只带首页 URL 的 Fragment，导致回到首页
        if (savedInstanceState != null) {
            // Activity 重建后旧标签的 WebView 已失效，只清引用，保留标签列表
            BrowserTabs.detachWebViews()
        } else {
            // 始终从持久化恢复标签列表：阅读器/搜索/字典等外部打开也先恢复，
            // 避免从空列表开始把已存的标签页覆盖或清空（标签只由用户主动新建/关闭）
            BrowserTabs.restore()
        }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.fragmentContainerView.id, BrowserPageFragment.newInstance(url, crossOriginMode, restoreTabs))
                .commitNowAllowingStateLoss()
        }

    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // 仅 App 整体退到后台时回收标签 WebView；内部页面切换不回收。
        // keepActive=true：保留当前激活标签的 WebView（进程内只留一个），切回时无需重建
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            (supportFragmentManager.findFragmentById(binding.fragmentContainerView.id)
                    as? BrowserPageFragment)?.onAppBackground(keepActive = true)
        }
    }

    override fun onPause() {
        // APP 退出/切到后台时趁窗口仍可见给当前激活标签截图（缩略图可靠），
        // 覆盖返回键到底、菜单退出、最近任务划掉等所有退出路径；
        // 销毁时的 onBackground 会跳过抓取，避免用拆卸期的空白覆盖这里写好的图。
        (supportFragmentManager.findFragmentById(binding.fragmentContainerView.id)
                as? BrowserPageFragment)?.let {
            Log.i("▶Thumb onPause capture activeTabId=${BrowserTabs.tabs.getOrNull(BrowserTabs.activeIndex)?.id}")
            it.captureActiveTabThumb()
        }
        // 提前落盘：防止被系统回收（如划掉最近任务）时丢失内存中的标签
        BrowserTabs.persist()
        super.onPause()
    }

    override fun onStop() {
        BrowserTabs.persist()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newUrl = intent.getStringExtra(URL).orEmpty()
        if (newUrl.isBlank()) return
        // 浏览器已存活（singleTask）：外部再次以指定网址打开，新建一个“可自动销毁”的临时标签
        val fragment = supportFragmentManager.findFragmentById(binding.fragmentContainerView.id)
            as? BrowserPageFragment ?: return
        fragment.openExternalUrl(newUrl)
    }

    override fun onResume() {
        super.onResume()
        // 阅读模式切回浏览器：消费待切换，切到来源标签并加载当前阅读页（此时 Activity 已可见）
        val tabId = BrowserTabs.pendingSwitchTabId
        val url = BrowserTabs.pendingSwitchUrl
        if (tabId == null || url == null) {
            Log.i("▶SwitchBrowser onResume pending=null (tabId=$tabId url=$url) -> skip")
            return
        }
        if (url.isBlank()) return
        Log.i("▶SwitchBrowser onResume consume tabId=$tabId url=$url")
        BrowserTabs.pendingSwitchTabId = null
        BrowserTabs.pendingSwitchUrl = null
        val fragment = supportFragmentManager.findFragmentById(binding.fragmentContainerView.id)
            as? BrowserPageFragment ?: run {
                Log.i("▶SwitchBrowser onResume fragment not found -> skip")
                return
            }
        fragment.switchToSourceTab(tabId, url)
    }

    override fun onDestroy() {
        if (isFinishing) {
            // 退出独立浏览器：先清理“外部指定网址自动创建”的临时标签
            // （返回栈到底的自动关闭，仍有历史的转为普通标签），再安全落库。
            BrowserTabs.finalizeTransientTabs()
            // 内存标签列表作为缓存保留（进程内单例），只做安全落库：
            // 逐条 upsert 存在的标签、按主键删除已关闭的标签，绝不清库覆盖。
            BrowserTabs.persist()
        }
        super.onDestroy()
    }

    companion object {
        const val RESTORE_TABS = "restore_tabs"

        fun startActivity(
            ctx: Context,
            url: String = "",
            crossOriginMode: Int = -1,
            restoreTabs: Boolean = false
        ) {
            if (url.isEmpty()) {
                Log.e("URL is empty, cannot start BrowserReaderActivity")
                return
            }
            ctx.startActivity(
                Intent(ctx, WebViewBrowserActivity::class.java).apply {
                    putExtra(URL, url)
                    putExtra(CROSS_ORIGIN_MODE, crossOriginMode)
                    putExtra(RESTORE_TABS, restoreTabs)
                }
            )
        }
    }
}