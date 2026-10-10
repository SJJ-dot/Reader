package com.sjianjun.reader.module.main

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.databinding.ActivityAppHelpBinding
import com.sjianjun.reader.utils.setDarkening
import com.sjianjun.reader.view.click
import sjj.alog.Log

/**
 * 使用说明：展示内置的离线说明页面（HTML）。
 *
 * - 强制确认模式（首次启动）：提供「退出」与「我知道了」，返回同样视为不同意；
 * - 普通模式（从「关于」页进入）：仅提供「我知道了」，返回即关闭。
 */
class AppHelpActivity : BaseActivity() {

    private val binding by lazy { ActivityAppHelpBinding.inflate(layoutInflater) }
    private val forceConfirm by lazy { intent.getBooleanExtra(EXTRA_FORCE_CONFIRM, false) }

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
        binding.toolbar.setNavigationOnClickListener { finishWithResult(false) }

        binding.webView.settings.setDarkening()
        binding.webView.setBackgroundColor(Color.TRANSPARENT)
        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    }.onFailure {
                        Log.e("打开链接失败：$url", it)
                    }
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

        // 仅强制确认模式（首次启动）展示「退出」按钮
        binding.exitButton.isVisible = forceConfirm
        binding.exitButton.click { finishWithResult(false) }
        binding.agreeButton.click { finishWithResult(true) }
    }

    /**
     * @param agreed true 表示同意（我知道了）；false 表示退出/关闭（不同意）。
     * 强制确认模式下返回键不设置结果，默认即为 RESULT_CANCELED，等同于不同意。
     */
    private fun finishWithResult(agreed: Boolean) {
        setResult(if (agreed) RESULT_OK else RESULT_CANCELED)
        finish()
    }

    private fun readHelpHtml(): String = runCatching {
        assets.open("app_help.html").bufferedReader().use { it.readText() }
    }.getOrElse {
        Log.e("读取内置使用说明失败", it)
        "<html><body style=\"padding:16px\">使用说明加载失败</body></html>"
    }

    companion object {
        const val EXTRA_FORCE_CONFIRM = "extra_force_confirm"

        fun startActivity(ctx: Context, forceConfirm: Boolean = false) {
            ctx.startActivity(
                Intent(ctx, AppHelpActivity::class.java)
                    .putExtra(EXTRA_FORCE_CONFIRM, forceConfirm)
            )
        }
    }
}
