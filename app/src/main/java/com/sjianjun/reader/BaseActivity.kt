package com.sjianjun.reader

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.gyf.immersionbar.ImmersionBar

abstract class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ImmersionBar.with(this).init()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out)
    }

    override fun startActivityForResult(intent: Intent?, requestCode: Int, options: Bundle?) {
        super.startActivityForResult(intent, requestCode, options)
        overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out)
    }
}