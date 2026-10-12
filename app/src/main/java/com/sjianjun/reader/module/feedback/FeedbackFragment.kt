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
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.databinding.FragmentFeedbackFragmentBinding
import kotlinx.coroutines.launch

/**
 * 意见反馈：页面（HTML/CSS/JS）内置于安装包，打开即渲染、首屏不联网、断网也能看界面。
 *
 * - 资源经 [WebViewAssetLoader] 从本地读取（[FeedbackAssetHandler]：热更新覆盖包优先、安装包兜底），
 *   并在后台静默比对线上版本做热更新，因此改反馈页不必再发 APP 版本；
 * - 页面在 appassets.androidplatform.net 下，直连云数据面会被 Origin 白名单挡掉，
 *   所以读写统一经 [FeedbackBridge] 转原生代发；
 * - 游客令牌随地址下发，用户无感；管理员口令在网页里输一次后由 localStorage 记住，
 *   下次打开自动进管理员页面。
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
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/$ASSET_DIR/", FeedbackAssetHandler(requireContext()))
            .build()

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
        webView.addJavascriptInterface(
            FeedbackBridge(webView, viewLifecycleOwner.lifecycleScope),
            BRIDGE_NAME
        )
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?, request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url ?: return null
                return assetLoader.shouldInterceptRequest(url)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return true
                // 站内导航留在当前 WebView，外链交给系统浏览器
                if (url.host == ASSET_HOST || url.host == feedbackHost) return false
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, url)) }
                return true
            }
        }
        webView.loadUrl(entryUrl())

        // 打开即用本地资源；热更新在后台静默进行，成功后下次打开生效
        viewLifecycleOwner.lifecycleScope.launch { FeedbackBundle.sync(requireContext()) }
    }

    /**
     * 页面入口：安装包内地址，带上游客令牌。
     * 网页若已记住管理员令牌，会自动改用权限更高的那一个。
     */
    private fun entryUrl(): String {
        val base = "https://$ASSET_HOST/$ASSET_DIR/${FeedbackBundle.ENTRY}"
        val token = BuildConfig.FEEDBACK_GUEST_TOKEN
        if (token.isBlank()) return base
        return "$base?t=$token"
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

    companion object {
        /** WebViewAssetLoader 约定的虚拟域名 */
        private const val ASSET_HOST = "appassets.androidplatform.net"
        private const val ASSET_DIR = "feedback"
        private const val BRIDGE_NAME = "FeedbackBridge"
    }
}
