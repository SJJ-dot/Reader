package com.sjianjun.reader

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.coroutineScope
import com.gyf.immersionbar.ImmersionBar
import com.sjianjun.reader.utils.handler
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

abstract class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ImmersionBar.with(this).init()
    }
}