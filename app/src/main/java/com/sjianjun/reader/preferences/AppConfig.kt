package com.sjianjun.reader.preferences

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.App
import com.sjianjun.reader.bean.Book

val globalConfig by lazy { AppConfig() }
val globalBookConfig by lazy { BookConfig() }

class AppConfig {

}

class BookConfig {

    private val config by lazy { App.app.getSharedPreferences("BookConfig", Context.MODE_PRIVATE) }

}