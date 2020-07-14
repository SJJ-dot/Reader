package com.sjianjun.reader.utils

import android.widget.Toast
import com.sjianjun.reader.App
import com.sjianjun.reader.coroutine.withMain

suspend fun toast(msg: String?, duration: Int = Toast.LENGTH_SHORT) {
    if (msg != null) {
        withMain {
            Toast.makeText(App.app, msg, duration).show()
        }
    }
}