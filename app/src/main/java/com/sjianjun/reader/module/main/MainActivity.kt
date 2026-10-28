package com.sjianjun.reader.module.main

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.github.liuyueyi.quick.transfer.constants.TransType
import com.sjianjun.coroutine.launchIo
import com.sjianjun.reader.App
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.R
import com.sjianjun.reader.URL
import com.sjianjun.reader.databinding.ActivityMainBinding
import com.sjianjun.reader.databinding.MainMenuNavHeaderBinding
import com.sjianjun.reader.handleDefaultException
import com.sjianjun.reader.module.bookcity.AdBlock
import com.sjianjun.reader.module.bookcity.BrowserPageFragment
import com.sjianjun.reader.module.bookcity.BrowserSession
import com.sjianjun.reader.module.bookcity.WebViewBrowserActivity
import com.sjianjun.reader.preferences.appEssentials
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.python.DnsWarmUp
import com.sjianjun.reader.repository.BookSourceUseCase
import com.sjianjun.reader.repository.BookUseCase
import com.sjianjun.reader.repository.BrowserDataMigration
import com.sjianjun.reader.utils.ActivityManger
import com.sjianjun.reader.utils.ChineseUtils
import com.sjianjun.reader.utils.isNight
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.click
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sjj.alog.Config
import sjj.alog.Log
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

private var first = true

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
        // 初始状态栏样式（浅色背景用深色图标）；进入应用后由 applyStatusBarForBrowser 按夜间模式/书城页调整
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        initBackPressed()
        globalConfig.privacyPolicyAccepted.observe(this) {
            if (it) {
                globalConfig.privacyPolicyAccepted.removeObservers(this)
                initialize()
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

    private fun initialize() {
        if (globalConfig.privacyPolicyAccepted.value != true) return
        if (!first) return
        first = false
        handleDefaultException(this)
        Config.getDefaultConfig().apply {
            consolePrintEnable = BuildConfig.DEBUG
            deleteOldLogFile = true
            consolePrintAllLog = true
            writeToFile = false
            writeToFileDir = File(applicationContext.externalCacheDir, "alog")
        }
        Python.start(AndroidPlatform(applicationContext))
        DnsWarmUp.start()
        when (globalConfig.readerJianFanMode.value) {
            1 -> {
                ChineseUtils.fixT2sDict()
                ChineseUtils.preLoad(true, TransType.SIMPLE_TO_TRADITIONAL)
            }

            2 -> ChineseUtils.preLoad(true, TransType.TRADITIONAL_TO_SIMPLE)
        }
        BrowserDataMigration.migrateIfNeeded()
        AdBlock.preload()
        App.app.appScope.launch(Dispatchers.IO) {
            runCatching { BookUseCase.resetBookLoadingState() }
                .onFailure { Log.e("重置书籍加载状态失败", it) }
        }
        App.app.appScope.launch {
            delay(5000.milliseconds)
            appEssentials.consecutiveCrashes = 0
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
            val navController = navController ?: return@setNavigationItemSelectedListener false
            // 先清空回退栈到根节点（不包含根节点本身）
            if (navController.currentDestination?.id != item.itemId) {
                navController.popBackStack(navController.graph.startDestinationId, false)
            }
            // 再导航到目标（如果当前不是目标页面）
            if (navController.currentDestination?.id != item.itemId) {
                if (item.itemId == R.id.browserBookCityFragment) {
                    // 书城以内嵌 Fragment 形式打开，参数与独立浏览器的入口保持一致：
                    // 优先恢复到上次退出时的页面，否则打开配置的主页；并恢复上次的标签列表
                    navController.navigate(
                        item.itemId,
                        Bundle().apply {
                            putString(URL, BrowserSession.consumeResume() ?: globalConfig.bookCityUrl)
                            putBoolean(WebViewBrowserActivity.RESTORE_TABS, true)
                        }
                    )
                } else {
                    navController.navigate(item.itemId)
                }
            }
            binding?.drawerLayout?.closeDrawer(GravityCompat.START)
            true
        }
        navController!!.addOnDestinationChangedListener { _, destination, _ ->
            val isBrowser = destination.id == R.id.browserBookCityFragment
            // 书城页直接移出 AppBarLayout，彻底避免 Toolbar 的绿色透到状态栏区域
            binding?.appBarLayout?.visibility = if (isBrowser) View.GONE else View.VISIBLE
            if (isBrowser) {
                supportActionBar?.hide()
            } else {
                supportActionBar?.show()
            }
            applyStatusBarForBrowser(isBrowser)
            // 让上面的 insets 回调重新执行一次，按当前目的地重新设置顶部留白
            binding?.root?.requestApplyInsets()
        }

        initDrawerMenuWidget()
        // addOnDestinationChangedListener 不会为初始目的地回调，这里补一次
        applyStatusBarForBrowser(navController?.currentDestination?.id == R.id.browserBookCityFragment)
        binding?.root?.requestApplyInsets()
    }

    @SuppressLint("SetTextI18n")
    private fun initDrawerMenuWidget() {
        val headerBinding = MainMenuNavHeaderBinding.bind(binding?.navUi?.getHeaderView(0)!!)
        ViewCompat.setOnApplyWindowInsetsListener(binding!!.root) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            headerBinding.navHeaderContainer.setPadding(0, statusBars.top, 0, 0)
            // DrawerLayout 的 fitsSystemWindows 已关闭，这里统一给主内容补上系统栏留白，
            // 保证所有页面的 Toolbar / 页面内容都不会顶到状态栏或导航栏下面。
            binding?.drawerContent?.setPadding(0, statusBars.top, 0, navBars.bottom)
            // 状态栏占位（位于 DrawerLayout 下层）：高度 = 状态栏高度，颜色按目的地切换
            binding?.statusBarBg?.let { bg ->
                val lp = bg.layoutParams
                if (lp.height != statusBars.top) {
                    lp.height = statusBars.top
                    bg.layoutParams = lp
                }
                bg.visibility = View.VISIBLE
            }
            insets
        }
        binding?.navUi?.getHeaderView(0)?.apply {
            if (appEssentials.appDayNightMode == MODE_NIGHT_NO) {
                headerBinding.dayNight.setImageResource(R.drawable.ic_theme_dark_24px)
            } else {
                headerBinding.dayNight.setImageResource(R.drawable.ic_theme_light_24px)
            }
            headerBinding.dayNight.click {
                when (appEssentials.appDayNightMode) {
                    MODE_NIGHT_NO -> {
                        headerBinding.dayNight.setImageResource(R.drawable.ic_theme_light_24px)
                        appEssentials.appDayNightMode = MODE_NIGHT_YES
                        setDefaultNightMode(MODE_NIGHT_YES)
                        //切换成深色模式。阅读器样式自动调整为上一次的深色样式
                        globalConfig.readerPageStyle.postValue(globalConfig.lastDarkTheme.value)
                    }

                    else -> {
                        headerBinding.dayNight.setImageResource(R.drawable.ic_theme_dark_24px)
                        appEssentials.appDayNightMode = MODE_NIGHT_NO
                        setDefaultNightMode(MODE_NIGHT_NO)
                        //切换成浅色模式。阅读器样式自动调整为上一次的浅色样式
                        globalConfig.readerPageStyle.postValue(globalConfig.lastLightTheme.value)
                    }
                }

            }
        }

    }

    /**
     * 书城页状态栏与独立浏览器（WebViewBrowserActivity）对齐：
     * - 书城：状态栏/导航栏区域用 dn_background_card（浏览器根布局底色），浅色主题用深色图标、深色主题用浅色图标；
     * - 其它页面：恢复 dn_background 底色 + 白色图标（与原来的 SystemBarStyle.dark 一致）。
     */
    private fun applyStatusBarForBrowser(browser: Boolean) {
        // 状态栏占位颜色：书城用页面底色；其它页面解析主题属性 colorPrimaryDark
        // （日间 = 深绿 #0c7e46，夜间主题 AppTheme.Dark 已覆盖为 dn_background 深色），
        // 不能直接取 R.color.colorPrimaryDark 资源——那是固定绿色，不会随夜间模式切换
        val barColor = if (browser) getColor(R.color.dn_background_card)
        else resolveThemeColor(androidx.appcompat.R.attr.colorPrimaryDark)
        binding?.statusBarBg?.setBackgroundColor(barColor)
        val bgColor = getColor(if (browser) R.color.dn_background_card else R.color.dn_background)
        window.setBackgroundDrawable(ColorDrawable(bgColor))
        binding?.root?.setBackgroundColor(bgColor)
        // API 35 以下保持状态栏透明，颜色统一由下层 status_bar_bg 负责
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.statusBarColor = Color.TRANSPARENT
        }
        // 与决定主题的 isNight 保持同一来源，避免主题/状态栏外观不同步
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
            browser && !isNight
    }

    /** 解析当前主题中的颜色属性（随夜间模式自动切换，如 colorPrimaryDark） */
    private fun resolveThemeColor(@AttrRes attr: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return if (typedValue.resourceId != 0) getColor(typedValue.resourceId) else typedValue.data
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                false
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // App 整体退到后台时回收书城标签的 WebView；内部页面切换不回收
        // keepActive=true：保留当前激活（可见）标签的 WebView，切回时无需重建/刷新页面
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            currentBrowserFragment()?.onAppBackground(keepActive = true)
        }
    }

    /** 当前展示中的书城 Fragment（不存在则返回 null） */
    private fun currentBrowserFragment(): BrowserPageFragment? {
        val host = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_main) as? NavHostFragment
        return host?.childFragmentManager?.fragments
            ?.filterIsInstance<BrowserPageFragment>()
            ?.firstOrNull { it.isAdded && it.view != null }
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
