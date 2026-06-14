package com.sjianjun.reader.module.bookcity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.URL
import com.sjianjun.reader.WEB_ID
import com.sjianjun.reader.databinding.ActivityBrowserReaderBinding
import sjj.alog.Log

class WebViewBrowserActivity : BaseActivity() {
    private val url by lazy { intent.getStringExtra(URL) ?: "" }
    private val webId by lazy { intent.getStringExtra(WEB_ID) ?: "" }
    private val binding by lazy { ActivityBrowserReaderBinding.inflate(layoutInflater) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (url.isBlank() && webId.isBlank()) {
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
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainerView.id, BookCityPageFragment.newInstance(url, webId))
            .commitNowAllowingStateLoss()

    }

    companion object {
        fun startActivity(ctx: Context, url: String = "", id: String = "") {
            if (url.isEmpty() && id.isBlank()) {
                Log.e("URL is empty, cannot start BrowserReaderActivity")
                return
            }
            ctx.startActivity(
                Intent(ctx, WebViewBrowserActivity::class.java).apply {
                    putExtra(URL, url)
                    putExtra(WEB_ID, id)
                }
            )
        }
    }
}