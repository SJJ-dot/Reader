package com.sjianjun.reader.module.main

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.sjianjun.coroutine.launchIo
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.module.update.checkUpdate
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.BookSourceMgr
import com.sjianjun.reader.repository.WebDavMgr
import com.sjianjun.reader.utils.ActivityManger
import com.umeng.commonsdk.UMConfigure
import kotlinx.android.synthetic.main.activity_main.drawer_content
import kotlinx.android.synthetic.main.activity_main.drawer_layout
import kotlinx.android.synthetic.main.activity_main.host_fragment_view_stub
import kotlinx.android.synthetic.main.activity_main.nav_ui
import kotlinx.android.synthetic.main.activity_main.toolbar
import kotlinx.android.synthetic.main.main_menu_nav_header.view.day_night
import kotlinx.coroutines.delay
import sjj.alog.Log


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
                BookSourceMgr.autoImport()
            }
            UMConfigure.init(application, UMConfigure.DEVICE_TYPE_PHONE, "")
            delay(5)
            Log.e("start test")
            val source = BookSource()
            source.lauanage = "py"
            source.js = """
                def search(query):
                    return "py search"


                def getDetails(bookUrl):
                    return "py getDetails"


                def getChapterContent(chapterUrl):
                    return "py getChapterContent"

            """.trimIndent()
            source.search("test search")
        }
        init()
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
