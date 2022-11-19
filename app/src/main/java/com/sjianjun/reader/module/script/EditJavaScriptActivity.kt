package com.sjianjun.reader.module.script

import android.os.Bundle
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ImmersionBar
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.repository.BookSourceManager
import com.sjianjun.reader.BOOK_SOURCE_ID
import com.sjianjun.reader.utils.toast
import kotlinx.android.synthetic.main.activity_edit_java_script.*
import kotlinx.coroutines.flow.firstOrNull

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
        save_script.setOnClickListener {
            launch {
                val bookSource = bookSource?:return@launch
                bookSource.apply {
                    name = script_source.text.toString()
                    js = script.text.toString()
                    version = script_version.text.toString().toIntOrNull() ?: 1
                }

                try {
                    BookSourceManager.saveJs(bookSource)
                    toast("脚本保存成功")
                    finish()
                } catch (e: Throwable) {
                    toast("脚本保存失败")
                }
            }
        }
        launch {
            val sourceId = intent.getStringExtra(BOOK_SOURCE_ID) ?: return@launch
            bookSource = BookSourceManager.getBookSourceById(sourceId).firstOrNull()
            script_source.setText(bookSource?.name)
            script.setText(bookSource?.js)
            //禁止修改脚本标志名称
//            if (!js?.name.isNullOrEmpty()) {
//                script_source.isEnabled = false
//            }
            script_version.setText((bookSource?.version ?: 1).toString())
        }
    }
}
