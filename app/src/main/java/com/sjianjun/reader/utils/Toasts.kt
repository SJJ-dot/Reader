package com.sjianjun.reader.utils

import android.widget.Toast
import com.sjianjun.coroutine.global
import com.sjianjun.reader.App
import kotlinx.coroutines.Dispatchers

fun toast(msg: String?, duration: Int = Toast.LENGTH_SHORT) {
    if (msg != null) {
        global(Dispatchers.Main) {
            Toast.makeText(App.app, msg, duration).show()
        }
    }
}