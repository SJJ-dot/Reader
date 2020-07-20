package com.sjianjun.reader.utils

import android.widget.Toast
import com.sjianjun.reader.App
import com.sjianjun.reader.coroutine.launchGlobal
import com.sjianjun.reader.coroutine.withMain
import kotlinx.coroutines.Dispatchers

fun toast(msg: String?, duration: Int = Toast.LENGTH_SHORT) {
    if (msg != null) {
        launchGlobal(Dispatchers.Main) {
            Toast.makeText(App.app, msg, duration).show()
        }
    }
}