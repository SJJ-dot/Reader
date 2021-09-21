package com.sjianjun.reader.utils

import android.content.Context
import android.os.Environment
import com.sjianjun.reader.BuildConfig
import java.io.File

object AppDirUtil {

    private lateinit var SD_CARD_ROOT :String

    lateinit var APP_DIR :String
        private set
    lateinit var APP_DATABASE_DIR :String
        private set
    lateinit var APP_DATABASE_FILE :String
        private set

    fun init(context: Context) {
        SD_CARD_ROOT = Environment.getExternalStorageDirectory().absolutePath + "/"
        APP_DIR = mkdir(SD_CARD_ROOT + BuildConfig.APPLICATION_ID + "/")
        APP_DATABASE_DIR = mkdir(APP_DIR+"database/")
        APP_DATABASE_FILE = "$APP_DATABASE_DIR/app_database"
    }
    private fun mkdir(path:String): String {
        File(path).mkdirs()
        return path
    }
}