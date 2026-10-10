package com.sjianjun.reader.module.script

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.bean.SourceUrl
import com.sjianjun.reader.databinding.ActivityBookSourceHelpBinding
import com.sjianjun.reader.repository.BookSourceUseCase
import com.sjianjun.reader.repository.SourceUrlUseCase
import com.sjianjun.reader.utils.setDarkening
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.launch
import sjj.alog.Log

/**
 * 书源说明：展示内置的离线说明页面，不再依赖外部网页链接。
 * 页面内的「一键导入测试书源」通过 [JsBridge] 回调本页面完成导入。
 */
@SuppressLint("SetJavaScriptEnabled")
class BookSourceHelpActivity : BaseActivity() {

    private val binding by lazy { ActivityBookSourceHelpBinding.inflate(layoutInflater) }

    /** 防止重复点击触发多次导入 */
    @Volatile
    private var importing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.webView.settings.apply {
            javaScriptEnabled = true
            setDarkening()
        }
        binding.webView.setBackgroundColor(Color.TRANSPARENT)
        binding.webView.addJavascriptInterface(JsBridge(), "Android")
        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    openInBrowser(url)
                    return true
                }
                return false
            }
        }
        binding.webView.loadDataWithBaseURL(
            "file:///android_asset/",
            readHelpHtml(),
            "text/html",
            "utf-8",
            null
        )
    }

    private fun readHelpHtml(): String = runCatching {
        assets.open("book_source_help.html").bufferedReader().use { it.readText() }
    }.getOrElse {
        Log.e("读取内置书源说明失败", it)
        "<html><body style=\"padding:16px\">书源说明加载失败</body></html>"
    }

    private fun openInBrowser(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }.onFailure {
            Log.e("打开链接失败：$url", it)
        }
    }

    /**
     * 一键导入测试书源：加入订阅链接并立即拉取导入
     */
    private fun startImportTestSource() {
        if (importing) {
            toast("正在导入，请稍候…")
            return
        }
        importing = true
        toast("正在导入测试书源…")
        lifecycleScope.launch {
            try {
                val sourceUrl = SourceUrl(TEST_SOURCE_URL, TEST_SOURCE_GROUP)
                SourceUrlUseCase.add(sourceUrl)
                val count = BookSourceUseCase.import(listOf(sourceUrl))
                if (count <= 0) {
                    toast("测试书源导入失败，请检查网络后重试")
                } else {
                    // 通知书源管理页在返回时刷新列表
                    setResult(RESULT_OK)
                }
            } catch (t: Throwable) {
                Log.e("测试书源导入失败", t)
                toast("测试书源导入失败：${t.message}")
            } finally {
                importing = false
            }
        }
    }

    /** 供内置说明页面调用的 JS 桥接 */
    private inner class JsBridge {
        @Suppress("unused")
        @JavascriptInterface
        fun importTestSource() {
            runOnUiThread { startImportTestSource() }
        }
    }

    companion object {
        /** 内置测试书源地址（Base64 + gzip 格式） */
        private const val TEST_SOURCE_URL =
            "https://cdn.jsdelivr.net/gh/SJJ-dot/Reader/test/BookSource/default.json.gzip"
        private const val TEST_SOURCE_GROUP = "修仙书源"

        fun startActivity(ctx: Context) {
            ctx.startActivity(Intent(ctx, BookSourceHelpActivity::class.java))
        }
    }
}
