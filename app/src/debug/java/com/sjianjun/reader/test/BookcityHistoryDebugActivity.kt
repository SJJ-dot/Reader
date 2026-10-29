package com.sjianjun.reader.test

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.sjianjun.reader.module.bookcity.BrowserPageFragment
import com.sjianjun.reader.module.bookcity.BrowserTabs
import com.sjianjun.reader.module.bookcity.CustomWebView
import java.io.File

/**
 * 真实 APP 内的历史回归测试：宿主【真实 BrowserPageFragment】（书城浏览器 Fragment），
 * 用真实书城网址 A->B->C 构建前进/后退历史，模拟“退出书城”(remove fragment -> onDestroyView -> onBackground)
 * 与“重进/冷启动”，验证重进后仍能后退。所有关键节点打 HISTBUG 日志，adb logcat -s HISTBUG 可读。
 *
 * 运行：
 *   adb shell am start -n com.sjianjun.reader.debug/com.sjianjun.reader.test.BookcityHistoryDebugActivity
 *   adb logcat -c ; (等待约 45s) ; adb logcat -d -s HISTBUG
 */
class BookcityHistoryDebugActivity : FragmentActivity() {

    private lateinit var tv: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var containerId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        tv = TextView(this)
        tv.textSize = 12f
        tv.setPadding(8, 8, 8, 8)
        root.addView(tv)
        val container = FrameLayout(this)
        containerId = View.generateViewId()
        container.id = containerId
        root.addView(container, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        handler.postDelayed({ start() }, 500)
    }

    private fun start() {
        try {
            // 清空旧标签 + 旧磁盘快照，保证从干净状态开始
            BrowserTabs.tabs.clear()
            BrowserTabs.persist()
            val dir = File(filesDir, "browser_state")
            if (dir.exists()) dir.deleteRecursively()

            val a = "https://m.qidian.com/"
            val b = "https://m.qidian.com/rank/"
            val c = "https://m.qidian.com/book/1049699858/"

            val frag1 = BrowserPageFragment.newInstance(a, -1, true)
            supportFragmentManager.beginTransaction().replace(containerId, frag1, "f1").commitNowAllowingStateLoss()
            Log.i("HISTBUG", "==== 添加 frag1(书城首页 A) ====")

            withCw(frag1) { cw ->
                step("加载 B") { cw.loadUrl(b) }
                after(7000) {
                    withCw(frag1) { cw2 ->
                        step("加载 C") { cw2.loadUrl(c) }
                        after(7000) {
                            withCw(frag1) { cw3 ->
                                val beforeExit = cw3.webView?.canGoBack() == true
                                Log.i("HISTBUG", "========== BEFORE EXIT canGoBack=$beforeExit ==========")
                                ui("退出前(在C) 能后退=$beforeExit")

                                // 模拟“退出书城”：移除 fragment -> onDestroyView -> onBackground（saveState + 落盘）
                                Log.i("HISTBUG", "==== 移除 frag1(退出书城) ====")
                                supportFragmentManager.beginTransaction().remove(frag1).commitNowAllowingStateLoss()

                                // 重进：新增 frag2
                                Log.i("HISTBUG", "==== 重进 frag2 ====")
                                val frag2 = BrowserPageFragment.newInstance(a, -1, true)
                                supportFragmentManager.beginTransaction().replace(containerId, frag2, "f2").commitNowAllowingStateLoss()
                                withCw(frag2) { cwR ->
                                    val reenter = cwR.webView?.canGoBack() == true
                                    Log.i("HISTBUG", "========== AFTER REENTER(same-process) canGoBack=$reenter ==========")
                                    ui("重进后(在C) 能后退=$reenter")

                                    // 模拟“进程被杀”冷启动：先彻底移除 frag2（避免共用单例互相销毁WebView），
                                    // 再清空内存 state/WebView（tab id 由 DB 持久化保留），重进 frag3 从磁盘恢复
                                    supportFragmentManager.beginTransaction().remove(frag2).commitNowAllowingStateLoss()
                                    Log.i("HISTBUG", "==== 冷启动：清空内存 state/WebView，重进 frag3 ====")
                                    BrowserTabs.tabs.forEach { it.state = null; it.webView = null }
                                    val frag3 = BrowserPageFragment.newInstance(a, -1, true)
                                    supportFragmentManager.beginTransaction().replace(containerId, frag3, "f3").commitNowAllowingStateLoss()
                                    withCw(frag3) { cwC ->
                                        val cold = cwC.webView?.canGoBack() == true
                                        Log.i("HISTBUG", "========== AFTER COLD START canGoBack=$cold ==========")
                                        ui("冷启动后(在C) 能后退=$cold")
                                        val ok = beforeExit && reenter && cold
                                        Log.i("HISTBUG", "RESULT beforeExit=$beforeExit reenter=$reenter cold=$cold => ${if (ok) "PASS" else "FAIL"}")
                                        ui("结果: beforeExit=$beforeExit reenter=$reenter cold=$cold => ${if (ok) "PASS ✅" else "FAIL ❌"}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e("HISTBUG", "TEST ERROR", t)
            ui("ERROR: ${t.message}")
        }
    }

    /** 等待 fragment 的真实 CustomWebView(根视图) 创建好再执行 action */
    private fun withCw(frag: Fragment, action: (CustomWebView) -> Unit) {
        val cw = frag.view as? CustomWebView
        if (cw != null && cw.webView != null) {
            action(cw)
        } else {
            handler.postDelayed({ withCw(frag, action) }, 500)
        }
    }

    private fun step(s: String, action: () -> Unit) {
        Log.i("HISTBUG", "---- $s ----")
        ui(s)
        action()
    }

    private fun after(ms: Long, block: () -> Unit) {
        handler.postDelayed(block, ms)
    }

    private fun ui(s: String) {
        runOnUiThread { tv.append("$s\n") }
    }
}
