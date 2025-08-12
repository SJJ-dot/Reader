package com.sjianjun.reader.module.shelf


import android.content.Context
import android.content.Intent
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
import androidx.activity.viewModels
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.WebBook
import com.sjianjun.reader.databinding.ActivityWebReaderBinding
import com.sjianjun.reader.databinding.FragmentBookCityPageHostItemBinding
import com.sjianjun.reader.module.bookcity.AdBlock
import com.sjianjun.reader.module.bookcity.HostStr
import com.sjianjun.reader.module.bookcity.contains
import com.sjianjun.reader.utils.color
import com.sjianjun.reader.utils.colorText
import com.sjianjun.reader.utils.htmlToSpanned
import com.sjianjun.reader.utils.init
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.click
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log
import java.io.ByteArrayInputStream

class WebReaderActivity : BaseActivity() {
    private val id by lazy { intent.getStringExtra(ID) ?: "" }
    private val binding by lazy { ActivityWebReaderBinding.inflate(layoutInflater) }
    private lateinit var adBlock: AdBlock
    private lateinit var book: WebBook
    private val viewModel: WebShelfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initData()
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

    private fun initData() {
        launch {
            val book = viewModel.getWebBookById(id).firstOrNull()
            if (book == null) {
                Log.w("WebBook not found for id: $id")
                toast("书籍不存在")
                return@launch
            }
            this@WebReaderActivity.book = book
            adBlock = AdBlock(book.id)

            initCtrlBtn()
            initAdBlock()
            initWebView()
            binding.webView.loadUrl(book.lastUrl ?: book.url)
        }
    }

    private fun initCtrlBtn() {
        binding.refresh.click {

            if (binding.refresh.isSelected) {
                binding.webView.stopLoading()
            } else {
                binding.webView.stopLoading()
                binding.webView.reload()
            }
        }
        binding.backward.click {
            Log.i("后退")
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            } else {
                Log.w("没有后退页面")
            }
        }
        binding.forward.click {
            Log.i("前进")
            if (binding.webView.canGoForward()) {
                binding.webView.goForward()
            } else {
                Log.w("没有前进页面")
            }
        }
        binding.menu.click {
            Log.i("打开菜单")
            binding.drawerLayout.openDrawer(GravityCompat.END)
        }
        binding.home.click {
            binding.webView.loadUrl(book.url)
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
        var lastTime = System.currentTimeMillis()
        setOnBackPressed {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.END)) {
                binding.drawerLayout.closeDrawer(GravityCompat.END)
                true
            } else {
                if (System.currentTimeMillis() - lastTime > 1000) {
                    toast("双击退出")
                    lastTime = System.currentTimeMillis()
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun initDrawer() {
        val hostListAdapter = HostListAdapter(adBlock)
        val blackListAdapter = BlackListAdapter(adBlock)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = hostListAdapter
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                Log.i("tab:${tab?.position}")
                binding.recyclerView.adapter = when (tab?.position) {
                    0 -> hostListAdapter
                    1 -> blackListAdapter
                    else -> hostListAdapter
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }
        })
        initAdBlockList(hostListAdapter, blackListAdapter)
    }

    private fun initAdBlockList(
        hostListAdapter: HostListAdapter,
        blackListAdapter: BlackListAdapter
    ) {
        hostListAdapter.adBlock.hostList.removeObservers(this)
        hostListAdapter.adBlock.blacklist.removeObservers(this)

        hostListAdapter.adBlock = adBlock
        blackListAdapter.adBlock = adBlock
        adBlock.hostList.observe(this) {
            hostListAdapter.data = it
            hostListAdapter.notifyDataSetChanged()
        }
        adBlock.blacklist.observe(this) {
            blackListAdapter.data = it
            blackListAdapter.notifyDataSetChanged()
        }
    }

    class HostListAdapter(var adBlock: AdBlock) :
        BaseAdapter<HostStr>(R.layout.fragment_book_city_page_host_item) {
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val binding = FragmentBookCityPageHostItemBinding.bind(holder.itemView)
            val host = data[position]
            val type = host.type.joinToString(
                ",",
                transform = { colorText(it, R.color.colorPrimary.color(holder.itemView.context)) })
            binding.tvHost.text = (host.host + "<br/>" + type).htmlToSpanned()
            binding.tvTime.text = host.time
            binding.btnMarkBlack.text = "+黑名单"
            binding.btnMarkBlack.click {
                adBlock.addBlackHost(data[position])
            }
        }
    }

    class BlackListAdapter(var adBlock: AdBlock) :
        BaseAdapter<HostStr>(R.layout.fragment_book_city_page_host_item) {
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val binding = FragmentBookCityPageHostItemBinding.bind(holder.itemView)
            val host = data[position]
            val type = host.type.joinToString(
                ",",
                transform = { colorText(it, R.color.colorPrimary.color(holder.itemView.context)) })
            binding.tvHost.text = (host.host + "<br/>" + type).htmlToSpanned()
            binding.tvTime.text = host.time
            binding.btnMarkBlack.text = "-移除"
            binding.btnMarkBlack.click {
                adBlock.removeBlackHost(data[position])
            }
        }
    }

    private fun initWebView() {
        val cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true); // 启用 Cookie 支持
        cookieManager.setAcceptThirdPartyCookies(binding.webView, true); // 启用第三方 Cookie

//chrome://inspect   edge://inspect
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
//声明WebSettings子类
        binding.webView.settings.init()

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


            override fun onPageFinished(webView: WebView?, url: String?) {
                book.lastUrl = url ?: ""
                book.lastTitle = webView?.title ?: ""
                book.updateTime = System.currentTimeMillis()
                Log.i("onPageFinished: $url, title: ${webView?.title}")
                launch {
                    viewModel.insertWebBook(book)
                }
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse? {
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
                adBlock.addUrl(request.url.toString())
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
        private const val ID = "ID"
        fun startActivity(ctx: Context, id: String) {
            ctx.startActivity(
                Intent(ctx, WebReaderActivity::class.java).apply {
                    putExtra(ID, id)
                }
            )
        }
    }
}