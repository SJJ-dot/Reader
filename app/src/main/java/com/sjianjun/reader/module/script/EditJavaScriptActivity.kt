package com.sjianjun.reader.module.script

import android.os.Bundle
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ImmersionBar
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.repository.JsManager
import com.sjianjun.reader.utils.JAVA_SCRIPT_SOURCE
import com.sjianjun.reader.utils.toast
import kotlinx.android.synthetic.main.activity_edit_java_script.*

class EditJavaScriptActivity : BaseActivity() {

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
                val javaScript = JavaScript(
                    source = script_source.text.toString(),
                    js = script.text.toString(),
                    version = script_version.text.toString().toIntOrNull() ?: 1,
                    isStartingStation = script_starting.isChecked,
                    priority = script_priority.text.toString().toIntOrNull() ?: 0
                )
                try {
                    JsManager.saveJs(javaScript)
                    toast("脚本保存成功")
                    finish()
                } catch (e: Throwable) {
                    toast("脚本保存失败")
                }
            }
        }
        launch {
            val source = intent.getStringExtra(JAVA_SCRIPT_SOURCE) ?: return@launch
            val js =  JsManager.getJs(source)
            script_source.setText(js?.source)
            script.setText(js?.js)
            //禁止修改脚本标志名称
            if (!js?.source.isNullOrEmpty()) {
                script_source.isEnabled = false
            }
            script_starting.isChecked = js?.isStartingStation ?: false
            script_version.setText((js?.version ?: 1).toString())
            script_priority.setText((js?.priority ?: 0).toString())
        }
    }
}
