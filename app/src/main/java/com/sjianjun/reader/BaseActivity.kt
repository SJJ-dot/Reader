package com.sjianjun.reader

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.utils.isNight

abstract class BaseActivity : AppCompatActivity() {
    private val onBackPressedListeners = mutableListOf<() -> Boolean>()
    open fun immersionBar() {

    }
    open fun initTheme(isNight:Boolean) {
        if (isNight) {
            setTheme(R.style.AppTheme_Dark)
        } else {
            setTheme(R.style.AppTheme)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        initTheme(isNight)
        super.onCreate(savedInstanceState)
        launch {
            immersionBar()
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        onBackPressedListeners.clear()
    }

    override fun startActivityForResult(intent: Intent?, requestCode: Int, options: Bundle?) {
        super.startActivityForResult(intent, requestCode, options)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onBackPressed() {
        if (onBackPressedListeners.find { it() } == null) {
            super.onBackPressed()
        }
    }

    fun setOnBackPressed(onBackPressed: () -> Boolean) {
        onBackPressedListeners.add(onBackPressed)
    }
}