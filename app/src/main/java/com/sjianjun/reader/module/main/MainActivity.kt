package com.sjianjun.reader.module.main

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import androidx.core.view.GravityCompat
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.sjianjun.coroutine.launchIo
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.ActivityMainBinding
import com.sjianjun.reader.databinding.MainMenuNavHeaderBinding
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.BookSourceUseCase
import com.sjianjun.reader.utils.ActivityManger
import com.sjianjun.reader.utils.WebDavMgr
import com.sjianjun.reader.utils.checkUpdate
import com.sjianjun.reader.view.click


class MainActivity : BaseActivity() {
    var binding: ActivityMainBinding? = null
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
                BookSourceUseCase.autoImport()
            }
//            com.sjianjun.reader.test.SourceTest.test()
        }
        init()
    }


    private fun init() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        binding?.hostFragmentViewStub?.inflate()
        binding?.drawerContent?.requestApplyInsets()

        setSupportActionBar(binding?.toolbar)

        navController = findNavController(R.id.nav_host_fragment_main)
        appBarConfiguration = AppBarConfiguration.Builder(navController!!.graph)
            .setOpenableLayout(binding?.drawerLayout)
            .build()

        NavigationUI.setupActionBarWithNavController(
            this,
            navController!!,
            appBarConfiguration!!
        )
        NavigationUI.setupWithNavController(binding?.navUi!!, navController!!)
        navController!!.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.browserBookCityFragment) {
                supportActionBar?.hide()
            } else {
                supportActionBar?.show()
            }
        }

        initDrawerMenuWidget()
        WebDavMgr.init()
    }

    private fun initDrawerMenuWidget() {
        val headerBinding = MainMenuNavHeaderBinding.bind(binding?.navUi?.getHeaderView(0)!!)
        binding?.navUi?.getHeaderView(0)?.apply {
            if (globalConfig.appDayNightMode == MODE_NIGHT_NO) {
                headerBinding?.dayNight?.setImageResource(R.drawable.ic_theme_dark_24px)
            } else {
                headerBinding.dayNight.setImageResource(R.drawable.ic_theme_light_24px)
            }
            headerBinding.dayNight.click {
                when (globalConfig.appDayNightMode) {
                    MODE_NIGHT_NO -> {
                        headerBinding.dayNight.setImageResource(R.drawable.ic_theme_light_24px)
                        globalConfig.appDayNightMode = MODE_NIGHT_YES
                        setDefaultNightMode(MODE_NIGHT_YES)
                        //切换成深色模式。阅读器样式自动调整为上一次的深色样式
                        globalConfig.readerPageStyle.postValue(globalConfig.lastDarkTheme.value)
                    }

                    else -> {
                        headerBinding.dayNight.setImageResource(R.drawable.ic_theme_dark_24px)
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
        if (binding?.drawerLayout?.isDrawerOpen(GravityCompat.START) == true) {
            binding?.drawerLayout?.closeDrawer(GravityCompat.START)
        } else {
            if (navController?.currentDestination?.id == R.id.bookShelfFragment) {
                finishAfterTransition()
            } else {
                super.onBackPressed()
            }
        }
    }


}
