package com.sjianjun.reader.module.bookcity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import com.sjianjun.reader.App
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.ActivityBookCityPageBinding
import com.sjianjun.reader.preferences.globalConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class BookCityPageActivity : BaseActivity() {
    var binding: ActivityBookCityPageBinding? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBookCityPageBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        binding?.apply {
            customWebView.init(lifecycle)

            //QQ登录
            globalConfig.qqAuthLoginUri.observe(this@BookCityPageActivity, Observer {
                val url = it?.toString() ?: return@Observer
                customWebView.loadUrl(url, true)
            })
            customWebView.loadUrl(intent.getStringExtra("url")!!, true)
        }
    }

    companion object {
        const val url = "BookCityPageActivity"
        fun startActivity(context: Context, url: String) {
            if (url.toHttpUrlOrNull() == null) return
            val intent = Intent(context, BookCityPageActivity::class.java)
            intent.putExtra("url", url)
            context.startActivity(intent)
        }
    }
}