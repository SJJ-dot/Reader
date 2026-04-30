package com.sjianjun.reader.module.main

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.sjianjun.coroutine.launchIo
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.ActivityMainBinding
import com.sjianjun.reader.databinding.MainMenuNavHeaderBinding
import com.sjianjun.reader.mqtt.OnlineInfo
import com.sjianjun.reader.mqtt.OnlineInfos
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.BookSourceUseCase
import com.sjianjun.reader.utils.ActivityManger
import com.sjianjun.reader.utils.checkUpdate
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.click
import kotlinx.coroutines.launch
import kotlin.math.roundToInt


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
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        launchIo {
            launchIo { checkUpdate(this@MainActivity, false) }
            launchIo {
                BookSourceUseCase.autoImport()
            }
//            com.sjianjun.reader.test.SourceTest.test()
        }
        init()
        initBackPressed()
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
    }

    @SuppressLint("SetTextI18n")
    private fun initDrawerMenuWidget() {
        val headerBinding = MainMenuNavHeaderBinding.bind(binding?.navUi?.getHeaderView(0)!!)
        ViewCompat.setOnApplyWindowInsetsListener(binding!!.root) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            headerBinding.navHeaderContainer.setPadding(0, statusBars.top, 0, 0)
            insets
        }
        binding?.navUi?.getHeaderView(0)?.apply {
            if (globalConfig.appDayNightMode == MODE_NIGHT_NO) {
                headerBinding.dayNight.setImageResource(R.drawable.ic_theme_dark_24px)
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
        renderOnlineInfo(headerBinding, OnlineInfos.onlineInfo.value)
        OnlineInfos.onlineInfo.observe(this) {
            renderOnlineInfo(headerBinding, it)
        }
        binding?.drawerLayout?.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: android.view.View) {
                lifecycleScope.launch {
                    OnlineInfos.refresh()
                }
            }
        })


    }

    @SuppressLint("SetTextI18n")
    private fun renderOnlineInfo(
        headerBinding: MainMenuNavHeaderBinding,
        info: OnlineInfo?,
    ) {
        val level = info?.level
        val levelName = when {
            level == null -> "--"
            level.stage_name.isNotBlank() -> level.stage_name
            level.major.isNotBlank() && level.stage > 0 -> "${level.major}${level.stage}层"
            level.major.isNotBlank() -> level.major
            else -> "Lv.${level.level_index}"
        }
        val progressPercent = when {
            level == null -> 0
            level.is_max_level -> 100
            else -> (level.progress.coerceIn(0.0, 1.0) * 100).roundToInt()
        }
        headerBinding.tvOnline.text = "书友在线：${info?.online_count ?: 0}"
        headerBinding.tvTodayOnline.text = "今日在线：${formatOnlineDuration(info?.today_online_seconds ?: 0)}"
        headerBinding.tvLevel.text = levelName
        headerBinding.levelProgress.progress = progressPercent
        headerBinding.tvTotalOnline.text = "总时长：${formatOnlineDuration(info?.total_online_seconds ?: 0)}"
    }

    private fun formatOnlineDuration(seconds: Int): String {
        val safeSeconds = seconds.coerceAtLeast(0)
        val hours = safeSeconds / 3600
        val minutes = (safeSeconds % 3600) / 60
        val remainSeconds = safeSeconds % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}小时${minutes}分"
            hours > 0 -> "${hours}小时"
            minutes > 0 -> "${minutes}分钟"
            else -> "${remainSeconds}秒"
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

    private var lastTime = 0L
    fun initBackPressed() {
        setOnBackPressed {
            if (binding?.drawerLayout?.isDrawerOpen(GravityCompat.START) == true) {
                binding?.drawerLayout?.closeDrawer(GravityCompat.START)
                true
            } else {
                if (navController?.currentDestination?.id == R.id.bookShelfFragment) {
                    if (System.currentTimeMillis() - lastTime > 1000) {
                        toast("双击退出")
                        lastTime = System.currentTimeMillis()
                        true
                    } else {
                        finishAfterTransition()
                        true
                    }
                } else {
                    false
                }
            }
        }

    }


}
