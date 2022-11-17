package com.sjianjun.reader.module.main

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatDelegate.*
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.sjianjun.coroutine.launchIo
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.module.update.checkUpdate
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.BookSourceManager
import com.sjianjun.reader.repository.WebDavMgr
import com.sjianjun.reader.utils.ActivityManger
import com.sjianjun.reader.utils.AppDirUtil
import com.sjianjun.reader.utils.toast
import com.umeng.commonsdk.UMConfigure
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.main_menu_nav_header.view.*

class MainActivity : BaseActivity() {

    private var navController: NavController? = null
    private var appBarConfiguration: AppBarConfiguration? = null
    override fun initTheme(isNight: Boolean) {
        if (isNight) {
            setTheme(R.style.Splash_noBackDark)
        } else {
            setTheme(R.style.Splash_noBack)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ActivityManger.finishSameType(this)
        super.onCreate(savedInstanceState)
        launchIo {
            launchIo { checkUpdate(this@MainActivity, false) }
            launchIo {
                BookSourceManager.autoImport()
            }
            UMConfigure.init(application, UMConfigure.DEVICE_TYPE_PHONE, "")
        }
        if (globalConfig.hasPermission) {
            XXPermissions.with(this)
                .permission(Permission.MANAGE_EXTERNAL_STORAGE)
                .request { _, all ->
                    globalConfig.hasPermission = all
                    if (!all) {
                        toast("本应用必须要存储卡读写权限用于保存数据库")
                        finish()
                    } else {
                        AppDirUtil.init(application)
                        init()
                    }
                }
        } else {
            AppDirUtil.init(application)
            init()
        }
    }


    private fun init() {

        setContentView(R.layout.activity_main)

        host_fragment_view_stub.inflate()
        drawer_content.requestApplyInsets()

        setSupportActionBar(toolbar)

        navController = findNavController(R.id.nav_host_fragment_main)
        appBarConfiguration = AppBarConfiguration.Builder(navController!!.graph)
            .setOpenableLayout(drawer_layout)
            .build()

        NavigationUI.setupActionBarWithNavController(
            this,
            navController!!,
            appBarConfiguration!!
        )
        NavigationUI.setupWithNavController(nav_ui, navController!!)
        navController!!.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.bookDetailsFragment -> {
                    drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                    supportActionBar?.show()
                }
                R.id.browserBookCityFragment -> {
                    supportActionBar?.hide()
                }
                else -> {
                    supportActionBar?.show()
                    drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                }
            }
        }

        initDrawerMenuWidget()
        WebDavMgr.init()
    }

    private fun initDrawerMenuWidget() {
        nav_ui.getHeaderView(0)?.apply {
            if (globalConfig.appDayNightMode == MODE_NIGHT_NO) {
                day_night.setImageResource(R.drawable.ic_theme_dark_24px)
            } else {
                day_night.setImageResource(R.drawable.ic_theme_light_24px)
            }
            day_night.setOnClickListener {
                when (globalConfig.appDayNightMode) {
                    MODE_NIGHT_NO -> {
                        day_night.setImageResource(R.drawable.ic_theme_light_24px)
                        globalConfig.appDayNightMode = MODE_NIGHT_YES
                        setDefaultNightMode(MODE_NIGHT_YES)
                        //切换成深色模式。阅读器样式自动调整为上一次的深色样式
                        globalConfig.readerPageStyle.postValue(globalConfig.lastDarkTheme.value)
                    }
                    else -> {
                        day_night.setImageResource(R.drawable.ic_theme_dark_24px)
                        globalConfig.appDayNightMode = MODE_NIGHT_NO
                        setDefaultNightMode(MODE_NIGHT_NO)
                        //切换成浅色模式。阅读器样式自动调整为上一次的浅色样式
                        globalConfig.readerPageStyle.postValue(globalConfig.lastLightTheme.value)
                    }
                }

            }
        }

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                false
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = navController ?: return super.onSupportNavigateUp()
        val appBarConfiguration = appBarConfiguration ?: return super.onSupportNavigateUp()
        return NavigationUI.navigateUp(
            navController,
            appBarConfiguration
        ) || super.onSupportNavigateUp()
    }

    override fun onBackPressed() {
        if (drawer_layout?.isDrawerOpen(GravityCompat.START) == true) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            if (navController?.currentDestination?.id == R.id.bookShelfFragment) {
                finishAfterTransition()
//                val intent = Intent()
//                intent.action = Intent.ACTION_MAIN
//                intent.addCategory(Intent.CATEGORY_HOME)
//                startActivity(intent)
            } else {
                super.onBackPressed()
            }
        }
    }
}
