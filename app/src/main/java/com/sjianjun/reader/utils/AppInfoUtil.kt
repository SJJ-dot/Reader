package com.sjianjun.reader.utils

import android.content.pm.PackageManager
import android.os.Build
import com.sjianjun.reader.App

object AppInfoUtil {
    fun metaData(key: String): String? {
        val metaData = App.app.packageManager.getApplicationInfo(
            App.app.packageName, PackageManager.GET_META_DATA
        )
        return metaData.metaData.getString(key)
    }

    fun versionName(): String {
        val info = App.app.packageManager.getPackageInfo(App.app.packageName, 0)
        return info.versionName ?: "0.0.0"
    }

    fun versionCode(): Long {
        val info = App.app.packageManager.getPackageInfo(App.app.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
    }
}