package com.sjianjun.reader.module.feedback

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.databinding.FragmentFeedbackFragmentBinding

/**
 * 意见反馈：内嵌网页版反馈服务（原 MQTT 反馈实现已移除）。
 *
 * - 游客令牌内置在安装包里（BuildConfig.FEEDBACK_GUEST_TOKEN），随地址下发，用户无需任何操作；
 * - 管理员口令即管理员令牌，在网页里输入一次后由 WebView 的 localStorage 记住，下次自动登录；
 * - 未携带有效令牌时网页只显示「需要访问令牌」，不会暴露反馈功能。
 */
class FeedbackFragment : BaseFragment() {

    private var binding: FragmentFeedbackFragmentBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentFeedbackFragmentBinding.inflate(inflater, container, false)
        this.binding = binding
        setupWebView(binding.webView, binding.progress)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(webView: WebView, progress: ProgressBar) {
        webView.settings.apply {
            javaScriptEnabled = true
            // 令牌记在 localStorage 里，必须打开 DOM Storage
            domStorageEnabled = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return true
                // 站内导航留在当前 WebView，外链交给系统浏览器
                if (url.host == feedbackHost) return false
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, url)) }
                return true
            }
        }
        webView.loadUrl(feedbackUrl())
    }

    /** 地址带上游客令牌；网页若已记住管理员令牌，会自动改用权限更高的那一个 */
    private fun feedbackUrl(): String {
        val base = BuildConfig.FEEDBACK_URL
        val token = BuildConfig.FEEDBACK_GUEST_TOKEN
        if (token.isBlank()) return base
        val sep = if (base.contains("?")) "&" else "?"
        return "$base${sep}t=$token"
    }

    private val feedbackHost: String?
        get() = runCatching { Uri.parse(BuildConfig.FEEDBACK_URL).host }.getOrNull()

    override fun onDestroyView() {
        binding?.webView?.apply {
            stopLoading()
            destroy()
        }
        binding = null
        super.onDestroyView()
    }
}
