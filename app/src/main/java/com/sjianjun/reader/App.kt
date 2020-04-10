package com.sjianjun.reader

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        app = this
        handleDefaultException(this)
    }


    companion object {
        lateinit var app:App
    }
}