package com.sjianjun.reader.module.bookcity

import android.content.Intent
import android.os.Bundle
import com.sjianjun.reader.TranslucentActivity
import com.sjianjun.reader.preferences.globalConfig
import sjj.alog.Log

class QQAuthLoginReceiveActivity : TranslucentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initData(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        initData(intent)
    }


    private fun initData(intent: Intent?) {
        if (intent?.data != null) {
            globalConfig.qqAuthLoginUri.postValue(intent.data)
            Log.i("QQAuthLoginReceiveActivity:${intent.data}")
        }
        finish()
    }
}