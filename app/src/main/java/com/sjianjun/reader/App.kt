package com.sjianjun.reader

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.ActivityManger

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        app = this
        handleDefaultException(this)
        ActivityManger.init(this)
        AppCompatDelegate.setDefaultNightMode(globalConfig.appDayNightMode)
    }


    companion object {
        lateinit var app:App
    }
}