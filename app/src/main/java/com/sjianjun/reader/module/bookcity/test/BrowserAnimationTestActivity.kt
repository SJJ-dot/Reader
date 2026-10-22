package com.sjianjun.reader.module.bookcity.test

import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sjianjun.reader.R
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.module.bookcity.AdBlock
import com.sjianjun.reader.module.bookcity.BookCityViewModel
import com.sjianjun.reader.module.bookcity.CustomWebView
import sjj.alog.Log

/**
 * 临时自测页：浏览器底栏动画（跟手 / 页面同步让位 / 一半吸附）。
 * 验证完删除。
 */
class BrowserAnimationTestActivity : BaseActivity() {

    private val vm by viewModels<BookCityViewModel>()
    private lateinit var webView: CustomWebView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        window.navigationBarColor = ContextCompat.getColor(this, R.color.dn_background_card)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        super.onCreate(savedInstanceState)
        webView = CustomWebView(this)
        val root = FrameLayout(this).apply {
            addView(
                webView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        setContentView(root)
        // 和 WebViewBrowserActivity 一致：给状态栏/导航栏留出系统栏高度
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        webView.init(this, AdBlock, null, vm)

        val html = "<html><body style='font-size:40px;line-height:2;margin:0'>" +
                (1..150).joinToString("") { "第 $it 行内容<br>" } + "</body></html>"
        webView.loadUrl("data:text/html;charset=utf-8," + Uri.encode(html))

        val handler = Handler(Looper.getMainLooper())
        if (intent.getBooleanExtra("debug_idle", false)) return
        if (intent.getBooleanExtra("debug_window", false)) {
            handler.postDelayed({ webView.debugWindowOpen() }, 2_500)
            return
        }
        if (intent.getBooleanExtra("debug_tabflow", false)) {
            handler.postDelayed({ webView.debugTabFlow() }, 3_000)
            return
        }
        if (intent.getBooleanExtra("debug_tabs", false)) {
            handler.postDelayed({ webView.debugTabs() }, 2_500)
            return
        }
        if (intent.getBooleanExtra("debug_menu", false)) {
            handler.postDelayed({ webView.debugMenu() }, 2_500)
            return
        }
        if (intent.getBooleanExtra("debug_sheet", false)) {
            handler.postDelayed({ webView.debugAddressSheet() }, 2_500)
            return
        }
        handler.postDelayed({
            // 按住拖动 100px 不放：页面应同步让位（offset + margin == height），且工具栏仍可见
            webView.debugHold(1000f, 900f)
            handler.postDelayed({
                val mid = webView.debugState()
                val parts = mid.replace("offset=", "").replace("hidden=", "")
                    .replace("margin=", "").replace("height=", "")
                    .split(" ").filter { it.isNotBlank() }
                val offset = parts.getOrNull(0)?.toIntOrNull() ?: -1
                val margin = parts.getOrNull(2)?.toIntOrNull() ?: -1
                val height = parts.getOrNull(3)?.toIntOrNull() ?: -1
                check(
                    "上滑时页面先铺满（无空白）",
                    mid.contains("hidden=false") && offset > 0 && margin == 0,
                    mid
                )

                // 保持按住，等外部截图；随后松手（不到一半）应回弹
                handler.postDelayed({
                    webView.debugRelease(900f)
                    handler.postDelayed({
                        val partial = webView.debugState()
                        check(
                            "半程松手回弹",
                            partial.contains("hidden=false") && !partial.contains("margin=0 "),
                            partial
                        )

                        webView.debugSwipe(1000f, 300f)
                        handler.postDelayed({
                            val full = webView.debugState()
                            check(
                                "全程松手收起",
                                full.contains("hidden=true") && full.contains("margin=0 "),
                                full
                            )
                            Log.i("浏览器动画自测 完成 mid=$mid partial=$partial full=$full")
                        }, 900)
                    }, 900)
                }, 2_500)
            }, 500)
        }, 2_500)
    }

    private fun check(name: String, pass: Boolean, state: String) {
        Log.i("浏览器动画自测 ${if (pass) "PASS" else "FAIL"} | $name | $state")
    }
}
