package com.sjianjun.reader.module.splash

import android.Manifest
import android.os.Bundle
import com.sjianjun.coroutine.launch
import com.sjianjun.permission.util.PermissionUtil
import com.sjianjun.permission.util.isGranted
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.R
import com.sjianjun.reader.module.main.MainActivity
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.APP_DATABASE_FILE
import com.sjianjun.reader.utils.APP_DATA_DIR
import com.sjianjun.reader.utils.startActivity
import com.sjianjun.reader.utils.toast
import java.io.File

class SplashActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Splash_noback)
        super.onCreate(savedInstanceState)
        PermissionUtil.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.INTERNET,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        ) { list ->
            if (!list.isGranted()) {
                launch {
                    toast("拒绝授权可能导致程序运行异常！")
                }
            }

            launch {
                initCopyDatabase()

                startActivity<MainActivity>()
                finish()
                globalConfig.lastAppVersion = BuildConfig.VERSION_CODE
                globalConfig.lastAppVersionName = BuildConfig.VERSION_NAME
            }


        }
    }

    /**
     * 数据库复制，将数据库存放到外部存储卡
     */
    private fun initCopyDatabase() {
        //376之后的版本升级修改了数据库设置
//        if (globalConfig.lastAppVersion < 376) {
//
//        }
        val dataBaseDir = File(APP_DATA_DIR)
        if (dataBaseDir.isDirectory && dataBaseDir.listFiles()?.isNotEmpty() == true) {
            return
        }
        if (!dataBaseDir.exists() || dataBaseDir.isFile) {
            dataBaseDir.delete()
        }
        dataBaseDir.mkdirs()
        val dataBaseFile = File(APP_DATABASE_FILE)

        val databasePath = getDatabasePath("app_database")
        if (databasePath.exists()) {
            databasePath.copyTo(dataBaseFile)
        }

    }
}