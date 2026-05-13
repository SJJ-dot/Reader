package com.sjianjun.reader.utils

import android.widget.Toast
import com.sjianjun.reader.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

fun toast(msg: String?, duration: Int = Toast.LENGTH_SHORT) {
    if (msg != null) {
        GlobalScope.launch(Dispatchers.Main) {
            Toast.makeText(App.app, msg, duration).show()
        }
    }
}