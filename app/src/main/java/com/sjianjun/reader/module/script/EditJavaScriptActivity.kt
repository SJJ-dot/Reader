package com.sjianjun.reader.module.script

import android.os.Bundle
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ImmersionBar
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BOOK_SOURCE_ID
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.databinding.ActivityEditJavaScriptBinding
import com.sjianjun.reader.repository.BookSourceMgr
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.click
import kotlinx.coroutines.flow.firstOrNull

class EditJavaScriptActivity : BaseActivity() {
    private var bookSource: BookSource? = null
    var binding: ActivityEditJavaScriptBinding? = null
    override fun immersionBar() {
        ImmersionBar.with(this)
            .hideBar(BarHide.FLAG_HIDE_STATUS_BAR)
            .init()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditJavaScriptBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        launch {
            val sourceId = intent.getStringExtra(BOOK_SOURCE_ID) ?: return@launch
            bookSource = BookSourceMgr.getBookSourceById(sourceId).firstOrNull()
            binding!!.script.setText(bookSource?.js)


        }

        binding!!.test.click {
            launch {
                bookSource?.let { it1 -> BookSourceMgr.saveJs(it1) }
                toast("书源保存成功")
            }
        }
    }
}
