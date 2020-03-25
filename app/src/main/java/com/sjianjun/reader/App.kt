package com.sjianjun.reader

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        handleDefaultException()
    }
}