package com.sjianjun.reader.module.main

import android.Manifest
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatDelegate.*
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.sjianjun.permission.util.PermissionUtil
import com.sjianjun.permission.util.isGranted
import com.sjianjun.reader.BaseAsyncActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.module.update.checkUpdate
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.test.BookCityTest
import com.sjianjun.reader.test.JavaScriptTest
import com.sjianjun.reader.test.ParseTest
import com.sjianjun.reader.utils.toast
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.main_menu_nav_header.view.*

class MainActivity : BaseAsyncActivity() {

    private var navController: NavController? = null
    private var appBarConfiguration: AppBarConfiguration? = null
    override val dispatchState: Boolean get() = true
    override val layoutRes: Int = R.layout.activity_main
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
                }
                else -> {
                    drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                }
            }
        }

        initDrawerMenuWidget()
    }

    override val onCreate: () -> Unit = {
        PermissionUtil.requestPermissions(
            this,
            arrayOf(Manifest.permission.INTERNET, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        ) { list ->
            if (!list.isGranted()) {
                launch {
                    toast("拒绝授权可能导致程序运行异常！")
                }
            }
        }

        launch {
            checkUpdate(this@MainActivity)
            JavaScriptTest.testJavaScript()
            ParseTest.test()
            BookCityTest.test()
        }
    }

    private fun initDrawerMenuWidget() {
        nav_ui.getHeaderView(0)?.apply {
            day_night.setOnClickListener {
                when (globalConfig.appDayNightMode) {
                    MODE_NIGHT_NO -> {
                        globalConfig.appDayNightMode = MODE_NIGHT_YES
                        setDefaultNightMode(MODE_NIGHT_YES)
                    }
                    else -> {
                        globalConfig.appDayNightMode = MODE_NIGHT_NO
                        setDefaultNightMode(MODE_NIGHT_NO)
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
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
