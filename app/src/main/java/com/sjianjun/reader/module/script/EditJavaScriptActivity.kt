package com.sjianjun.reader.module.script

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BOOK_SOURCE_ID
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.databinding.ActivityEditJavaScriptBinding
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.click

class EditJavaScriptActivity : BaseActivity() {
    private val viewModel by viewModels<EditJavaScriptViewModel>()
    private var bookSource: BookSource? = null
    private var binding: ActivityEditJavaScriptBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityEditJavaScriptBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding!!.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            val offset = (imeInsets.bottom - systemBars.bottom).coerceAtLeast(0)
            v.translationY = -offset.toFloat()
            insets
        }
        launch {
            val sourceId = intent.getStringExtra(BOOK_SOURCE_ID) ?: return@launch
            bookSource = viewModel.getBookSource(sourceId)
            binding!!.script.setText(bookSource?.js)


        }

        binding!!.test.click {
            launch {
                bookSource?.let { it1 -> viewModel.saveJs(it1) }
                toast("书源保存成功")
            }
        }

        var lastTime = 0L
        setOnBackPressed {
            val rootInsets = ViewCompat.getRootWindowInsets(binding!!.root)
            val imeVisible = rootInsets?.isVisible(WindowInsetsCompat.Type.ime()) == true
            if (imeVisible) {
                WindowInsetsControllerCompat(window, binding!!.root).hide(WindowInsetsCompat.Type.ime())
                return@setOnBackPressed true
            }
            if (System.currentTimeMillis() - lastTime > 1000) {
                toast("双击退出")
                lastTime = System.currentTimeMillis()
                return@setOnBackPressed true
            } else {
                return@setOnBackPressed false
            }
        }
    }
}
