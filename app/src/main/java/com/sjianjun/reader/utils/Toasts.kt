package com.sjianjun.reader.utils

import android.widget.Toast
import com.sjianjun.reader.App

suspend fun toastSHORT(msg: String?) {
    if (msg != null) {
        withMain {
            Toast.makeText(App.app, msg, Toast.LENGTH_SHORT).show()
        }
    }
}