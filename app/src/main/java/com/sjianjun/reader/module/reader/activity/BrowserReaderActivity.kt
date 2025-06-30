package com.sjianjun.reader.module.reader.activity

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.enableEdgeToEdge
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.WEB_VIEW_UA_ANDROID
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.databinding.ActivityBrowserReaderBinding
import com.sjianjun.reader.databinding.FragmentBookCityPageHostItemBinding
import com.sjianjun.reader.module.bookcity.AdBlock
import com.sjianjun.reader.module.bookcity.HostStr
import com.sjianjun.reader.module.bookcity.contains
import com.sjianjun.reader.utils.gone
import com.sjianjun.reader.utils.setDarkening
import com.sjianjun.reader.utils.toast
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log
import java.io.ByteArrayInputStream

class BrowserReaderActivity : BaseActivity() {
    private val url by lazy { intent.getStringExtra(URL) ?: "" }
    private val binding by lazy { ActivityBrowserReaderBinding.inflate(layoutInflater) }
    private val adBlock by lazy { AdBlock(url) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (url.isEmpty()) {
            Log.e("URL is empty, finish activity")
            finish()
            return
        }
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initCtrlBtn()
        initAdBlock()
        initWebView()
        binding.webView.loadUrl(url)
    }

    override fun onPause() {
        super.onPause()
        binding.webView.pauseTimers()
        binding.webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.resumeTimers()
        binding.webView.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.webView.destroy()
    }

    private fun initCtrlBtn() {
        binding.refresh.setOnClickListener {

            if (binding.refresh.isSelected) {
                binding.webView.stopLoading()
            } else {
                binding.webView.stopLoading()
                binding.webView.reload()
            }
        }
        binding.backward.setOnClickListener {
            Log.i("后退")
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            } else {
                Log.w("没有后退页面")
            }
        }
        binding.forward.setOnClickListener {
            Log.i("前进")
            if (binding.webView.canGoForward()) {
                binding.webView.goForward()
            } else {
                Log.w("没有前进页面")
            }
        }
        binding.menu.setOnClickListener {
            Log.i("打开菜单")
            binding.drawerLayout.openDrawer(GravityCompat.END)
        }
        binding.home.setOnClickListener {
            finish()
        }
    }

    private fun initAdBlock() {
        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            var first = true
            override fun onDrawerOpened(drawerView: View) {
                if (first) {
                    first = false
                    initDrawer()
                }
            }
        })

        setOnBackPressed {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.END)) {
                binding.drawerLayout.closeDrawer(GravityCompat.END)
                true
            } else if (binding.webView.canGoBack()) {
                binding.webView.goBack()
                true
            } else {
                false
            }
        }
    }

    private fun initDrawer() {
        val hostListAdapter = HostListAdapter(adBlock)
        val whiteListAdapter = WhiteListAdapter(adBlock)
        val blackListAdapter = BlackListAdapter(adBlock)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = hostListAdapter
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                Log.i("tab:${tab?.position}")
                binding.recyclerView.adapter = when (tab?.position) {
                    0 -> hostListAdapter
                    1 -> whiteListAdapter
                    2 -> blackListAdapter
                    else -> hostListAdapter
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }
        })
        initAdBlockList(hostListAdapter, whiteListAdapter, blackListAdapter)
    }

    private fun initAdBlockList(
        hostListAdapter: HostListAdapter,
        whiteListAdapter: WhiteListAdapter,
        blackListAdapter: BlackListAdapter
    ) {
        hostListAdapter.adBlock.hostList.removeObservers(this)
        hostListAdapter.adBlock.whitelist.removeObservers(this)
        hostListAdapter.adBlock.blacklist.removeObservers(this)

        hostListAdapter.adBlock = adBlock
        whiteListAdapter.adBlock = adBlock
        blackListAdapter.adBlock = adBlock
        adBlock.hostList.observe(this) {
            hostListAdapter.data = it
            hostListAdapter.notifyDataSetChanged()
        }
        adBlock.blacklist.observe(this) {
            blackListAdapter.data = it
            blackListAdapter.notifyDataSetChanged()
        }
        adBlock.whitelist.observe(this) {
            whiteListAdapter.data = it
            whiteListAdapter.notifyDataSetChanged()
        }
    }

    class HostListAdapter(var adBlock: AdBlock) : BaseAdapter<HostStr>(R.layout.fragment_book_city_page_host_item) {
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val binding = FragmentBookCityPageHostItemBinding.bind(holder.itemView)
            binding.tvHost.text = data[position].host
            binding.tvTime.text = data[position].time
            binding.btnMarkWhite.text = "+白名单"
            binding.btnMarkWhite.setOnClickListener {
                adBlock.addWhiteHost(data[position])
            }
            binding.btnMarkBlack.text = "+黑名单"
            binding.btnMarkBlack.setOnClickListener {
                adBlock.addBlackHost(data[position])
            }
        }
    }

    class WhiteListAdapter(var adBlock: AdBlock) : BaseAdapter<HostStr>(R.layout.fragment_book_city_page_host_item) {
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val binding = FragmentBookCityPageHostItemBinding.bind(holder.itemView)
            binding.tvHost.text = data[position].host
            binding.tvTime.text = data[position].time
            binding.btnMarkBlack.gone()
            binding.btnMarkWhite.text = "移除白名单"
            binding.btnMarkWhite.setOnClickListener {
                adBlock.removeWhiteHost(data[position])
            }
        }

    }

    class BlackListAdapter(var adBlock: AdBlock) : BaseAdapter<HostStr>(R.layout.fragment_book_city_page_host_item) {
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val binding = FragmentBookCityPageHostItemBinding.bind(holder.itemView)
            binding.tvHost.text = data[position].host
            binding.tvTime.text = data[position].time
            binding.btnMarkWhite.gone()
            binding.btnMarkBlack.text = "移除黑名单"
            binding.btnMarkBlack.setOnClickListener {
                adBlock.removeBlackHost(data[position])
            }
        }
    }

    private fun initWebView() {
        val cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true); // 启用 Cookie 支持
//        cookieManager.setAcceptThirdPartyCookies(webView, true); // 启用第三方 Cookie

//        WebView.setWebContentsDebuggingEnabled(true)
//声明WebSettings子类
        val webSettings = binding.webView.settings
        webSettings.setDarkening()
        webSettings.userAgentString = WEB_VIEW_UA_ANDROID
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.loadsImagesAutomatically = true //支持自动加载图片
//设置自适应屏幕，两者合用
        webSettings.useWideViewPort = true //将图片调整到适合webview的大小
        webSettings.loadWithOverviewMode = true // 缩放至屏幕的大小
        webSettings.blockNetworkImage = false //设置图片加载方式，默认true，表示不加载图片
//缩放操作
        webSettings.setSupportZoom(true) //支持缩放，默认为true。是下面那个的前提。
        webSettings.builtInZoomControls = true //设置内置的缩放控件。若为false，则该WebView不可缩放
        webSettings.displayZoomControls = false //隐藏原生的缩放控件
        binding.webView.scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
        binding.webView.isScrollbarFadingEnabled = false

        binding.webView.webViewClient = object : WebViewClient() {


            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                Log.i("shouldOverrideUrlLoading:$url ")
                if (url.endsWith(".apk")) {
                    return true
                }
                val httpUrl = url.toHttpUrlOrNull()
                if (adBlock.blacklist.contains(httpUrl?.host) ||
                    adBlock.blacklist.contains(httpUrl?.topPrivateDomain())
                ) {
                    return true
                }
                if (request.url?.scheme == "http" || request.url?.scheme == "https") {
                    // 处理 http 和 https 的链接
                    return false // 返回 false 以让 WebView 加载该链接
                }
                return true

            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            }

            override fun onPageFinished(webView: WebView?, url: String?) {

            }

            override fun onLoadResource(view: WebView?, url: String?) {
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse? {
                adBlock?.addUrl(request.url.toString())
                if (adBlock?.blacklist.contains(request.url.host) || adBlock?.blacklist.contains(
                        request.url.toString().toHttpUrlOrNull()?.topPrivateDomain()
                    )
                ) {
                    Log.i("拦截请求：${request.url}")
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        ByteArrayInputStream("".toByteArray())
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                Log.e("SSL Error: ${error?.toString()}")
                // 忽略 SSL 错误
                handler?.proceed()
            }


        }
        binding.webView.webChromeClient =
            object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    Log.i("progress:${newProgress} ")
                    binding.searchRefresh.progress = newProgress
                    binding.backward.isEnabled = binding.webView.canGoBack()
                    binding.forward.isEnabled = binding.webView.canGoForward()
                    if (newProgress == 100) {
                        binding.refresh.isSelected = false
                        binding.searchRefresh.visibility = GONE
                    } else {
                        binding.refresh.isSelected = true
                        binding.searchRefresh.visibility = VISIBLE
                    }
                }
            }
    }

    companion object {
        private const val URL = "url"
        fun startActivity(ctx: Context, url: String) {
            if (url.isEmpty()) {
                Log.e("URL is empty, cannot start BrowserReaderActivity")
                return
            }
            ctx.startActivity(
                Intent(ctx, BrowserReaderActivity::class.java).apply {
                    putExtra(URL, url)
                }
            )
        }
    }
}