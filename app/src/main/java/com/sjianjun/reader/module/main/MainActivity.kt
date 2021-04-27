package com.sjianjun.reader.module.main

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatDelegate.*
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.sjianjun.coroutine.launch
import com.sjianjun.coroutine.runOnIdle
import com.sjianjun.reader.BaseAsyncActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.module.update.loadUpdateInfo
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.test.JavaScriptTest
import com.sjianjun.reader.test.ParseTest
import com.sjianjun.reader.utils.ActivityManger
import com.tencent.bugly.beta.Beta
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.main_menu_nav_header.view.*

class MainActivity : BaseAsyncActivity() {

    private var navController: NavController? = null
    private var appBarConfiguration: AppBarConfiguration? = null
    override val layoutRes: Int = R.layout.activity_main
    override val fadeOut: Boolean = false
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_DayNight)
        ActivityManger.finishSameType(this)
        super.onCreate(savedInstanceState)
    }

    override val onLoadedView: (View) -> Unit = {
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

        runOnIdle {

            launch {
                JavaScriptTest.testJavaScript()
                ParseTest.test()
                //GitHub 更新信息
                loadUpdateInfo()
                //bugly 更新信息
                Beta.checkAppUpgrade(false, false)
            }
        }
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
//                finishAfterTransition()
                val intent = Intent()
                intent.action = Intent.ACTION_MAIN
                intent.addCategory(Intent.CATEGORY_HOME)
                startActivity(intent)
            } else {
                super.onBackPressed()
            }
        }
    }
}
