package com.sjianjun.reader.module.script

import android.os.Bundle
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ImmersionBar
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BOOK_SOURCE_ID
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.repository.BookSourceMgr
import com.sjianjun.reader.utils.toast
import kotlinx.android.synthetic.main.activity_edit_java_script.script
import kotlinx.android.synthetic.main.activity_edit_java_script.test
import kotlinx.coroutines.flow.firstOrNull
import sjj.alog.Log

class EditJavaScriptActivity : BaseActivity() {
    private var bookSource: BookSource? = null
    override fun immersionBar() {
        ImmersionBar.with(this)
            .hideBar(BarHide.FLAG_HIDE_STATUS_BAR)
            .init()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_java_script)
        launch {
            val sourceId = intent.getStringExtra(BOOK_SOURCE_ID) ?: return@launch
            bookSource = BookSourceMgr.getBookSourceById(sourceId).firstOrNull()
            script.setText(bookSource?.js)


        }

        test.setOnClickListener {
            launch {
                bookSource?.let { it1 -> BookSourceMgr.saveJs(it1) }
                toast("书源保存成功")
            }
        }
    }
}
