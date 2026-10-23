package com.sjianjun.reader.module.main

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.sjianjun.coroutine.launchIo
import com.sjianjun.reader.App
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.ActivityMainBinding
import com.sjianjun.reader.databinding.MainMenuNavHeaderBinding
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.BookSourceUseCase
import com.sjianjun.reader.utils.ActivityManger
import com.sjianjun.reader.utils.checkUpdate
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.click


class MainActivity : BaseActivity() {
    var binding: ActivityMainBinding? = null
    private var navController: NavController? = null
    private var appBarConfiguration: AppBarConfiguration? = null
    private val helpLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // 已阅读并同意使用说明，进入主界面
            globalConfig.privacyPolicyAccepted.postValue(true)
        } else {
            // 不同意则退出应用
            ActivityManger.exitApp()
        }
    }
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
        if (globalConfig.autoCheckUpdate.value == true) {
            launchIo {
                checkUpdate(this@MainActivity, false)
            }
        }
        initBackPressed()
        globalConfig.privacyPolicyAccepted.observe(this) {
            if (it) {
                globalConfig.privacyPolicyAccepted.removeObservers(this)
                App.app.initialize()
                init()
                launchIo {
                    BookSourceUseCase.autoImport()
                }
            } else {
                helpLauncher.launch(
                    Intent(this, AppHelpActivity::class.java)
                        .putExtra(AppHelpActivity.EXTRA_FORCE_CONFIRM, true)
                )
            }
        }
    }


    private fun init() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        binding?.hostFragmentViewStub?.inflate()
        binding?.drawerContent?.requestApplyInsets()

        setSupportActionBar(binding?.toolbar)

        navController = findNavController(R.id.nav_host_fragment_main)
        appBarConfiguration = AppBarConfiguration.Builder(
            R.id.bookShelfFragment,
            R.id.webShelfFragment,
            R.id.searchFragment,
            R.id.browserBookCityFragment,
            R.id.novelScriptFragment,
            R.id.settingsFragment,
            R.id.feedbackFragment,
            R.id.aboutFragment
        )
            .setOpenableLayout(binding?.drawerLayout)
            .build()

        NavigationUI.setupActionBarWithNavController(
            this,
            navController!!,
            appBarConfiguration!!
        )
        // 覆盖默认的 setupWithNavController 行为：导航前清空回退栈，
        // 避免从子页面（如在线排行）切抽屉菜单后栈里残留子页面。
        binding?.navUi?.setNavigationItemSelectedListener { item ->
            // 书城不再用内部 Fragment，直接跳转独立的浏览器 Activity
            if (item.itemId == R.id.browserBookCityFragment) {
                binding?.drawerLayout?.closeDrawer(GravityCompat.START)
                startActivity(
                    android.content.Intent(
                        this,
                        com.sjianjun.reader.module.bookcity.WebViewBrowserActivity::class.java
                    ).apply {
                        putExtra(
                            com.sjianjun.reader.URL,
                            com.sjianjun.reader.module.bookcity.BrowserSession.consumeResume()
                                ?: com.sjianjun.reader.preferences.globalConfig.bookCityUrl
                        )
                        putExtra(
                            com.sjianjun.reader.module.bookcity.WebViewBrowserActivity.RESTORE_TABS,
                            true
                        )
                    }
                )
                return@setNavigationItemSelectedListener true
            }
            val navController = navController ?: return@setNavigationItemSelectedListener false
            // 先清空回退栈到根节点（不包含根节点本身）
            if (navController.currentDestination?.id != item.itemId) {
                navController.popBackStack(navController.graph.startDestinationId, false)
            }
            // 再导航到目标（如果当前不是目标页面）
            if (navController.currentDestination?.id != item.itemId) {
                navController.navigate(item.itemId)
            }
            binding?.drawerLayout?.closeDrawer(GravityCompat.START)
            true
        }
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
                        toast("再按一次退出应用")
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
