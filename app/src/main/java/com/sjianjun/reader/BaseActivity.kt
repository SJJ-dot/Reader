package com.sjianjun.reader

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.sjianjun.reader.utils.isNight

abstract class BaseActivity : AppCompatActivity() {
    open fun initTheme(isNight: Boolean) {
        if (isNight) {
            setTheme(R.style.AppTheme_Dark)
        } else {
            setTheme(R.style.AppTheme)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        initTheme(isNight)
        super.onCreate(savedInstanceState)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        super.startActivityForResult(intent, requestCode, options)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    fun setOnBackPressed(onBackPressed: () -> Boolean) {
        onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                if(!onBackPressed()){
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }
}