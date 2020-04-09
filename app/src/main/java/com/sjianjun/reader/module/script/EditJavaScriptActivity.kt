package com.sjianjun.reader.module.script

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.JAVA_SCRIPT_SOURCE
import com.sjianjun.reader.utils.toastSHORT
import kotlinx.android.synthetic.main.activity_edit_java_script.*
import kotlinx.coroutines.flow.first
import java.lang.Exception

class EditJavaScriptActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_java_script)
        base_url.setText(globalConfig.javaScriptBaseUrl)
        save_base_url.setOnClickListener {
            globalConfig.javaScriptBaseUrl = base_url.text.toString()
        }
        save_script.setOnClickListener {
            viewLaunch {
                try {
                    DataManager.insertJavaScript(
                        JavaScript(
                            source = script_source.text.toString(),
                            js = script.text.toString()
                        )
                    )
                    toastSHORT("保存成功")
                    finish()
                } catch (e: Throwable) {
                    toastSHORT("保存失败")
                }
            }
        }
        viewLaunch {
            val source = intent.getStringExtra(JAVA_SCRIPT_SOURCE) ?: return@viewLaunch
            val js = DataManager.getJavaScript(source).first()
            script_source.setText(js?.source)
            script.setText(js?.js)
        }
    }
}
