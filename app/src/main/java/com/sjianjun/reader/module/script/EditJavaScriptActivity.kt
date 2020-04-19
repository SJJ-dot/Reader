package com.sjianjun.reader.module.script

import android.os.Bundle
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ImmersionBar
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.JAVA_SCRIPT_SOURCE
import com.sjianjun.reader.utils.toastSHORT
import kotlinx.android.synthetic.main.activity_edit_java_script.*
import kotlinx.coroutines.flow.first

class EditJavaScriptActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_java_script)
        ImmersionBar.with(this)
            .hideBar(BarHide.FLAG_HIDE_STATUS_BAR)
            .init()
        save_script.setOnClickListener {
            launch {
                val javaScript = JavaScript(
                    source = script_source.text.toString(),
                    js = script.text.toString()
                )
                try {
                    try {
                        DataManager.insertJavaScript(javaScript)
                        toastSHORT("创建脚本成功")
                    } catch (e: Exception) {
                        DataManager.updateJavaScript(javaScript)
                        toastSHORT("脚本已更新")
                    }

                    finish()
                } catch (e: Throwable) {
                    toastSHORT("脚本保存失败")
                }
            }
        }
        launch {
            val source = intent.getStringExtra(JAVA_SCRIPT_SOURCE) ?: return@launch
            val js = DataManager.getJavaScript(source).first()
            script_source.setText(js?.source)
            script.setText(js?.js)
            //禁止修改脚本标志名称
            if (!js?.source.isNullOrEmpty()) {
                script_source.isEnabled = false
            }
        }
    }
}
