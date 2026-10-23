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
import com.sjianjun.reader.WEB_ID
import com.sjianjun.reader.databinding.ActivityBrowserReaderBinding
import sjj.alog.Log

class WebViewBrowserActivity : BaseActivity() {
    private val url by lazy { intent.getStringExtra(URL) ?: "" }
    private val webId by lazy { intent.getStringExtra(WEB_ID) ?: "" }
    private val crossOriginMode by lazy { intent.getIntExtra(CROSS_ORIGIN_MODE, -1) }
    /** 是否恢复上次的标签列表：仅书城主入口为 true，字典/搜索等外部打开为 false */
    private val restoreTabs by lazy { intent.getBooleanExtra(RESTORE_TABS, false) }
    private val binding by lazy { ActivityBrowserReaderBinding.inflate(layoutInflater) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (url.isBlank() && webId.isBlank()) {
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
        } else if (restoreTabs) {
            BrowserTabs.restore()
        }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.fragmentContainerView.id, BrowserPageFragment.newInstance(url, webId, crossOriginMode, restoreTabs))
                .commitNowAllowingStateLoss()
        }

    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // 仅 App 整体退到后台时回收标签 WebView；内部页面切换不回收
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            (supportFragmentManager.findFragmentById(binding.fragmentContainerView.id)
                    as? BrowserPageFragment)?.onAppBackground()
        }
    }

    override fun onStop() {
        BrowserTabs.persist()
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) {
            if (BrowserTabs.tabs.isEmpty()) {
                // 没有剩余标签：清空持久化，下次进入自动重建主页标签
                BrowserTabs.clearPersisted()
            } else {
                // 还有剩余标签：保留，下次进入恢复
                BrowserTabs.persist()
            }
            BrowserTabs.tabs.clear()
        }
        super.onDestroy()
    }

    companion object {
        const val RESTORE_TABS = "restore_tabs"

        fun startActivity(ctx: Context, url: String = "", id: String = "", crossOriginMode: Int = -1) {
            if (url.isEmpty() && id.isBlank()) {
                Log.e("URL is empty, cannot start BrowserReaderActivity")
                return
            }
            ctx.startActivity(
                Intent(ctx, WebViewBrowserActivity::class.java).apply {
                    putExtra(URL, url)
                    putExtra(WEB_ID, id)
                    putExtra(CROSS_ORIGIN_MODE, crossOriginMode)
                }
            )
        }
    }
}