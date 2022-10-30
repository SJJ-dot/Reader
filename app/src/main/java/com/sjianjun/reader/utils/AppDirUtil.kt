package com.sjianjun.reader.utils

import android.app.Application
import android.content.Context
import android.os.Environment
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.preferences.globalConfig
import sjj.alog.Log
import java.io.File

object AppDirUtil {


    lateinit var APP_DATABASE_FILE: String
        private set

    fun init(context: Application) {
        migrateOldDb(context)
        val newDbDir = context.getDir("database", Context.MODE_PRIVATE)
        APP_DATABASE_FILE = File(newDbDir, "app_database").absolutePath
    }

    private fun migrateOldDb(context: Application) {
        if (!globalConfig.hasPermission) {
            return
        }
        val newDbDir = context.getDir("database", Context.MODE_PRIVATE)
        val root = Environment.getExternalStorageDirectory().absolutePath + "/"
        val appDir = mkdir(root + BuildConfig.APPLICATION_ID + "/")
        val oldDbDir = mkdir(appDir + "database/")
        if (!File(oldDbDir).exists()) {
            return
        }
        if (File(oldDbDir).listFiles().isNullOrEmpty()) {
            return
        }
        File(oldDbDir).copyRecursively(newDbDir, overwrite = true)
        File(appDir).deleteRecursively()
        globalConfig.hasPermission = false
        Log.e("数据库迁移成功")
    }

    private fun mkdir(path: String): String {
        File(path).mkdirs()
        return path
    }
}