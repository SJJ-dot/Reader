package com.sjianjun.reader

import android.os.Bundle

open class TranslucentActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.translucent)
        super.onCreate(savedInstanceState)
    }
}