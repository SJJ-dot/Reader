package com.sjianjun.reader.utils

import android.widget.Toast
import com.sjianjun.reader.App

suspend fun toast(msg: String?, duration: Int = Toast.LENGTH_SHORT) {
    if (msg != null) {
        withMain {
            Toast.makeText(App.app, msg, duration).show()
        }
    }
}