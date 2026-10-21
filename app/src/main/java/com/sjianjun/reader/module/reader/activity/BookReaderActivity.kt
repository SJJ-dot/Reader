package com.sjianjun.reader.module.reader.activity

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.distinctUntilChanged
import com.sjianjun.coroutine.launch
import com.sjianjun.coroutine.withMain
import com.sjianjun.reader.BOOK_ID
import com.sjianjun.reader.BOOK_TITLE
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.SHOW_TTS_CARD
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.databinding.ActivityBookReaderBinding
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.event.observe
import com.sjianjun.reader.module.bookcity.WebViewBrowserActivity
import com.sjianjun.reader.module.main.ChapterListFragment
import com.sjianjun.reader.module.reader.BookReaderSettingFragment
import com.sjianjun.reader.module.reader.ReaderClickAreaAction
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.ReplacementRuleUseCase
import com.sjianjun.reader.tts.LocatedLine
import com.sjianjun.reader.tts.TtsChapterSplitter
import com.sjianjun.reader.tts.TtsPlaybackController
import com.sjianjun.reader.tts.TtsPlaybackMode
import com.sjianjun.reader.tts.TtsPlaybackState
import com.sjianjun.reader.tts.v2.load.NovelChapterLoader
import com.sjianjun.reader.utils.fragmentCreate
import com.sjianjun.reader.utils.showSnackbar
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import sjj.alog.Log
import sjj.novel.view.reader.bean.BookRecordBean
import sjj.novel.view.reader.page.ChapterPageCache
import sjj.novel.view.reader.page.CustomPageStyle
import sjj.novel.view.reader.page.DisplayParams
import sjj.novel.view.reader.page.PageLoader
import sjj.novel.view.reader.page.PageLoader.Companion.STATUS_FINISH
import sjj.novel.view.reader.page.PageLoader.Companion.STATUS_LOADING
import sjj.novel.view.reader.page.PageMode
import sjj.novel.view.reader.page.PageStyle
import sjj.novel.view.reader.page.PageView
import sjj.novel.view.reader.page.TxtChapter
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** 通知权限申请码（Android 13+ 通知需要运行时权限） */
private const val REQUEST_CODE_NOTIFICATION = 1001

/** 等待朗读目标章节排版完成的最长重试次数（100ms * 30 = 3 秒） */
private const val MAX_TTS_CHAPTER_WAIT = 30

class BookReaderActivity : BaseActivity() {
    var binding: ActivityBookReaderBinding? = null
    private val TAG_SETTING_DIALOG = "BookReaderSettingFragment"
    private val bookId get() = intent.getStringExtra(BOOK_ID)!!

    // delegate TTS operations through TtsService which picks the engine by config
    private val ttsService = com.sjianjun.reader.utils.TtsService
    private val mPageLoader get() = binding?.pageView?.pageLoader
    private val viewModel by viewModels<BookReaderViewModel>()

    private var chapterCacheJob: Job? = null

    /** 章节内容加载任务：同一时刻只保留一个，避免自己和自己并发重复加载 */
    private var chapterRequestJob: Job? = null

    /** 阅读页是否可见：不可见时不再预加载后面的章节（此时由朗读服务自己预加载） */
    private var pageVisible = true

    /** 朗读跟随任务：切章 + 定位高亮，新的进度到来时取消旧的 */
    private var ttsFollowJob: Job? = null

    /** 已跟随的朗读位置（章节链接:段落序号），位置没变就不重复定位重绘 */
    private var ttsFollowKey: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        // 在 Activity 生命周期入口先应用方向策略，减少折叠切换时方向闪动。
        applyReaderOrientation(globalConfig.readerOrientationMode.value ?: 0)
        super.onCreate(savedInstanceState)
        binding = ActivityBookReaderBinding.inflate(layoutInflater)
        val pageStyle = PageStyle.getStyle(globalConfig.readerPageStyle.value)
        enableEdgeToEdge(pageStyle.isDark)
        setContentView(binding!!.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding!!.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.displayCutout
            val safeLeft = maxOf(systemBars.left, cutout?.safeInsetLeft ?: 0)
            val safeTop = maxOf(systemBars.top, cutout?.safeInsetTop ?: 0)
            val safeRight = maxOf(systemBars.right, cutout?.safeInsetRight ?: 0)
            val safeBottom = maxOf(systemBars.bottom, cutout?.safeInsetBottom ?: 0)
            val params = DisplayParams()
            mPageLoader?.mDisplayParams?.setInsets(
                left = max(params.insetLeft, safeLeft.toFloat()),
                top = max(params.insetTop, safeTop.toFloat()),
                right = max(params.insetRight, safeRight.toFloat()),
                bottom = max(params.insetBottom, safeBottom.toFloat()),
            )
            binding?.drawerChapterList?.setPadding(0, safeTop, safeRight, 0)
            insets
        }

        initSettingMenu()
        Log.i("BookReaderActivity onCreate savedInstanceState: $savedInstanceState")
        initData()
        initView()
        initBackPressed()
    }

    override fun onResume() {
        super.onResume()
        applyReaderBrightness()
        pageVisible = true
        // 阅读页在前台时阅读进度由阅读页维护，朗读服务不写
        TtsPlaybackController.setReaderVisible(true)
    }

    override fun onPause() {
        super.onPause()
        pageVisible = false
        TtsPlaybackController.setReaderVisible(false)
    }

    override fun onDestroy() {
        // 阅读页关闭：取消还在跑的章节预加载（朗读服务会按自己的需要预加载）
        chapterRequestJob?.cancel()
        chapterRequestJob = null
        //退出阅读页时书籍可能还在加载中（章节列表上拉检查更新／书籍信息加载）：
        //上面的协程取消会连带取消正在跑的脚本调用，加载流程自己会恢复"加载中"状态
        super.onDestroy()
        // 朗读由前台服务持有，退出阅读页不再停止朗读，只解除跟随
        mPageLoader?.setTtsSpeakLine(null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.i("onNewIntent: ${intent.getStringExtra(BOOK_ID)}")
        setIntent(intent)
        initData()
        handleShowTtsPlayCard()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (globalConfig.readerVolumeKeyPageTurn.value == true && ttsService.isSpeaking.value != true) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    handleReaderClickAction(ReaderClickAreaAction.PREV_PAGE)
                    return true
                }

                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    handleReaderClickAction(ReaderClickAreaAction.NEXT_PAGE)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    fun initBackPressed() {
        setOnBackPressed {
            val fragment = getSettingFragment()
            when {
                binding?.drawerLayout!!.isDrawerOpen(GravityCompat.END) -> {
                    binding?.drawerLayout!!.closeDrawer(GravityCompat.END)
                    true
                }

                binding?.drawerLayout!!.isDrawerOpen(GravityCompat.START) -> {
                    binding?.drawerLayout!!.closeDrawer(GravityCompat.START)
                    true
                }

                fragment?.isVisible == true -> {
                    supportFragmentManager.beginTransaction()
                        .hide(fragment)
                        .commitAllowingStateLoss()
                    true
                }

                else -> false
            }
        }

    }

    private fun enableEdgeToEdge(isDark: Boolean) {
        if (isDark) {
            enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        } else {
            enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        }
    }

    private fun initSettingMenu() {
        // 朗读由前台服务负责，阅读页只是观察者：跟随服务上报的进度高亮与翻页
        TtsPlaybackController.state.observe(this) { state ->
            onTtsStateChanged(state)
        }
        observe<String>(EventKey.CHAPTER_SPEAK) {
            launch {
                val activeState = TtsPlaybackController.state.value
                // 正在朗读的就是本书 -> 停止；否则按「开始朗读当前书」处理（会替换掉其他会话）
                if (activeState != null && TtsPlaybackController.isActiveForBook(bookId)) {
                    TtsPlaybackController.stop()
                    mPageLoader?.setTtsSpeakLine(null)
                    Log.i("停止朗读")
                    return@launch
                }
                if (mPageLoader?.pageStatus == STATUS_LOADING) {
                    toast("书籍正在加载中")
                    return@launch
                }
                val chapterIndex = mPageLoader?.chapterPos ?: 0
                val chapter = viewModel.chapterList.value?.getOrNull(chapterIndex)
                if (chapter == null) {
                    toast("当前章节获取失败")
                    return@launch
                }
                viewModel.getChapterContent(chapter, 0)
                if (!chapter.isLoaded || chapter.content == null) {
                    toast("章节未加载成功 $chapter")
                    return@launch
                }
                if (chapter.content?.firstOrNull()?.contentError == true) {
                    toast("章节内容错误 $chapter")
                    return@launch
                }
                requestNotificationPermissionIfNeeded()
                // 从当前阅读页的开始朗读：跨页段落从本页第一行开始，不回翻上一页
                val position = TtsChapterSplitter.paragraphPositionAtPage(
                    mPageLoader?.curPageList,
                    mPageLoader?.pagePos ?: 0
                )
                Log.i(
                    "开始朗读章节$chapterIndex ${chapter.title} " +
                        "起始段落:${position.index + 1} 段内偏移:${position.offsetChars}"
                )
                TtsPlaybackController.startBook(
                    chapter.bookId,
                    chapterIndex,
                    position.index,
                    position.offsetChars
                )
            }
        }

        observe<String>(EventKey.CHAPTER_LIST_CAHE) {
            if (chapterCacheJob?.isActive == true) {
                chapterCacheJob?.cancel()
                viewModel.chapterCache.postValue(false)
                toast("已取消章节缓存")
                return@observe
            }
            // 批量缓存交给加载层：逐章整章加载（首页 + 全部分页），可取消、带进度
            val chapterList = viewModel.chapterList.value ?: return@observe
            val start = max(0, mPageLoader?.chapterPos ?: 0)
            viewModel.chapterCache.postValue(true)
            toast("章节缓存：开始")
            chapterCacheJob = NovelChapterLoader.instance.cacheBatch(chapterList, start) { done, total ->
                if (done >= total) {
                    runOnUiThread {
                        if (viewModel.chapterCache.value == true) {
                            viewModel.chapterCache.postValue(false)
                            toast("章节缓存：完成")
                        }
                    }
                }
            }
        }
        observe<String>(EventKey.CHAPTER_LIST) {
            binding?.drawerLayout!!.openDrawer(GravityCompat.END)
        }
        observe<String>(EventKey.BROWSER_OPEN) {
            //使用浏览器打开书籍链接
            val chapterIdx = mPageLoader?.curChapter?.chapterIndex ?: -1
            var url = viewModel.chapterList.value?.getOrNull(chapterIdx)?.url
            if (url == null) {
                url = viewModel.book.value?.url
            }
            if (url.isNullOrEmpty()) {
                toast("书籍链接为空")
                return@observe
            }
            WebViewBrowserActivity.startActivity(this, url)
        }
        observe<String>(EventKey.CHAPTER_SYNC_FORCE) {
            launch("CHAPTER_SYNC_FORCE") {
                val loader = mPageLoader ?: return@launch
                if (loader.pageStatus == STATUS_LOADING) {
                    return@launch
                }
                loader.pageStatus = STATUS_LOADING

                loader.curChapter?.let { curChapter ->
                    val chapter = viewModel.chapterList.value?.getOrNull(curChapter.chapterIndex) ?: return@launch
                    viewModel.getChapterContent(chapter, 1)
                    curChapter.content = chapter.content?.joinToString("\n") { it.format() }
                    if (chapter.content?.firstOrNull()?.contentError == true) {
                        curChapter.title = chapter.title + "(章节内容错误)"
                    } else {
                        curChapter.title = chapter.title
                    }
                    Log.e("加载完成 ${curChapter.chapterIndex} ${curChapter.title}")
                    loader.reloadPages()
                }
            }
        }
        observe<String>(EventKey.CHAPTER_CONTENT_ERROR) {
            launch {
                val loader = mPageLoader ?: return@launch
                if (loader.pageStatus == STATUS_LOADING) {
                    toast("书籍正在加载中")
                    return@launch
                }
                val txtChapter = loader.curChapter
                val curChapter = loader.chapterPos
                val chapter = viewModel.chapterList.value?.getOrNull(curChapter)
                if (chapter == null) {
                    toast("当前章节获取失败")
                    return@launch
                }
                viewModel.getChapterContent(chapter, 0)
                if (!chapter.isLoaded || chapter.content?.isEmpty() != false) {
                    Log.i("章节未加载成功 ${chapter.title} isLoaded:${chapter.isLoaded} ${chapter.content}")
                    toast("章节未加载成功")
                    return@launch
                }
                viewModel.maskChapterContentErr(chapter, txtChapter)
                loader.reloadPages()

            }
        }

        observe<String>(EventKey.REPLACEMENT_RULES_CHANGED) {
            launch {
                mPageLoader?.setReplacementRules(ReplacementRuleUseCase.getEnabledRules())
            }
        }
        observe<String>(EventKey.BOOK_SOURCE_CHANGED) {
            intent.putExtra(BOOK_ID, it)
            initData()
        }

        globalConfig.readerPageMode.observe(this) {
            mPageLoader?.setPageMode(PageMode.entries.getOrNull(it) ?: PageMode.SIMULATION)
        }
        val text = MediatorLiveData<Int>()
        text.addSource(globalConfig.readerFontSize) {
            text.value = it
        }
        text.addSource(globalConfig.readerLineSpacing) {
            text.value = it
        }
        text.addSource(globalConfig.readerParaSpacing) {
            text.value = it
        }
        text.addSource(globalConfig.readerLetterSpacing) {
            text.value = it
        }
        text.distinctUntilChanged().observe(this) {
            val textSize = globalConfig.readerFontSize.value ?: return@observe
            val lineSpace = globalConfig.readerLineSpacing.value ?: return@observe
            val paraSpace = globalConfig.readerParaSpacing.value ?: return@observe
            val letterSpacing = globalConfig.readerLetterSpacing.value ?: return@observe
            mPageLoader?.setTextSize(textSize.toFloat(), lineSpace.toFloat(), paraSpace.toFloat(), letterSpacing.toFloat() / 100)
        }
        observe<CustomPageStyle>(EventKey.CUSTOM_PAGE_STYLE) {
            mPageLoader?.setPageStyle(it, true)
            enableEdgeToEdge(it.isDark)
        }
        globalConfig.readerPageStyle.observe(this) {
            val pageStyle = PageStyle.getStyle(it)
            enableEdgeToEdge(pageStyle.isDark)
            mPageLoader?.setPageStyle(pageStyle)
        }

        globalConfig.readerFontFamily.observe(this) {
            if (it.isAsset) {
                if (it.resId == 0) {
                    mPageLoader?.setTypeface(null)
                } else {
                    val typeface = ResourcesCompat.getFont(this, it.resId)
                    mPageLoader?.setTypeface(typeface)
                }
            } else {
                val typeface = Typeface.createFromFile(File(it.path!!))
                mPageLoader?.setTypeface(typeface)
            }
        }

        globalConfig.readerJianFanMode.observe(this) {
            mPageLoader?.setJianFanMode(it)
        }

        globalConfig.readerTypesettingMode.observe(this) {
            mPageLoader?.setTypesettingMode(it)
        }

        globalConfig.readerTextGridAlign.observe(this) {
            mPageLoader?.setGridAlign(it)
        }

        globalConfig.readerFullWidthConvert.observe(this) {
            mPageLoader?.setFullWidthConvert(it)
        }

        globalConfig.readerOrientationMode.observe(this) {
            applyReaderOrientation(it)
        }
        globalConfig.readerScreenKeepOn.observe(this) {
            if (it == true) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        globalConfig.readerBrightnessPercent.observe(this) {
            applyReaderBrightness()
        }

        binding!!.pageView.setTouchListener(object : PageView.TouchListener {
            override fun intercept(event: MotionEvent?): Boolean {
                //隐藏设置对话框
                return getSettingFragment()?.let {
                    if (it.isVisible && event?.action == MotionEvent.ACTION_UP) {
                        supportFragmentManager.beginTransaction()
                            .hide(it)
                            .commitAllowingStateLoss()
                    }
                    return@let it.isVisible

                } ?: false
            }

            override fun onTap(x: Float, y: Float, viewWidth: Int, viewHeight: Int) {
                val actions = ReaderClickAreaAction.normalize(globalConfig.readerClickAreaActions.value)
                val cellIndex = ReaderClickAreaAction.cellIndexOf(x, y, viewWidth, viewHeight)
                val action = actions.getOrElse(cellIndex) { ReaderClickAreaAction.MENU }
                handleReaderClickAction(action)
            }

        })
    }

    private fun handleReaderClickAction(action: Int) {
        when (action) {
            ReaderClickAreaAction.PREV_PAGE -> binding?.pageView?.performPageTap(false)
            ReaderClickAreaAction.NEXT_PAGE -> binding?.pageView?.performPageTap(true)
            ReaderClickAreaAction.MENU -> showSettingMenu()

            ReaderClickAreaAction.PREV_CHAPTER -> mPageLoader?.skipPreChapter()
            ReaderClickAreaAction.NEXT_CHAPTER -> mPageLoader?.skipNextChapter()
            ReaderClickAreaAction.DIRECTORY -> binding?.drawerLayout?.openDrawer(GravityCompat.END)
            ReaderClickAreaAction.NONE ->  /*无操作*/ {}
        }
    }

    private fun showSettingMenu() {
        getSettingFragment(create = true)?.let {
            supportFragmentManager.beginTransaction()
                .show(it)
                .commitAllowingStateLoss()
        }
    }

    /**
     * 从朗读通知 / 播放卡片左侧按钮进来时，直接唤起设置菜单（菜单里有朗读播放卡片）
     */
    private fun handleShowTtsPlayCard() {
        if (!intent.getBooleanExtra(SHOW_TTS_CARD, false)) return
        intent.removeExtra(SHOW_TTS_CARD)
        Log.i("唤起朗读播放菜单")
        launch {
            // 稍等一下让阅读页初始化完，避免和首屏抢时序
            delay(300)
            showSettingMenu()
        }
    }

    private fun getSettingFragment(create: Boolean = false): BookReaderSettingFragment? {
        val fragment = supportFragmentManager.findFragmentByTag(TAG_SETTING_DIALOG)
        if (fragment != null && fragment is BookReaderSettingFragment) {
            return fragment
        }
        if (create) {
            Log.i("创建设置对话框")
            val newFragment = BookReaderSettingFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.fl_setting_container, newFragment, TAG_SETTING_DIALOG)
                .hide(newFragment)
                .commitAllowingStateLoss()
            return newFragment
        }
        return null
    }

    private fun initView() {
        viewModel.book.observe(this) { book ->
            book ?: return@observe
            Log.i("设置章节列表 ChapterListFragment")
            val fragment = supportFragmentManager.findFragmentByTag(book.title)
            if (fragment != null && fragment is ChapterListFragment && fragment.bookTitle == book.title) {
                Log.i("章节列表已存在，直接使用")
            } else {
                Log.i("章节列表不存在，创建新的")
                supportFragmentManager.beginTransaction()
                    .replace(R.id.drawer_chapter_list, fragmentCreate<ChapterListFragment>(BOOK_TITLE to book.title), book.title)
                    .commitAllowingStateLoss()
            }
        }
        val loader = mPageLoader
        loader?.setOnPageChangeListener(object : PageLoader.OnPageChangeListener {

            override fun requestChapters(requestChapters: MutableList<TxtChapter>) {
                Log.i("加载章节内容 $requestChapters")

                // 不再取消上一次请求：上一次往往正在预加载下一章，取消会把预加载中断
                // （日志：没有等待者 -> 共享加载被取消 -> 章节加载失败）。
                // 同一章由 NovelChapterLoader 的共享加载去重，不会重复抓。
                chapterRequestJob = launch("requestChapters") {
                    requestChapters.forEach { requestChapter ->
                        // 阅读页不在前台时只保证当前章有内容，后面的章节交给朗读服务预加载
                        if (!pageVisible && requestChapter.chapterIndex != loader.chapterPos) {
                            Log.i("阅读页不在前台，跳过预加载：${requestChapter.title}")
                            return@forEach
                        }
                        val chapter = viewModel.book.value?.chapterList?.getOrNull(requestChapter.chapterIndex) ?: return@launch
                        // 加载失败会重试多次：失败一次就不再请求会让这一章一直没内容
                        loadChapterContentWithRetry(chapter, requestChapter)
                        requestChapter.content = chapter.content?.joinToString("\n") { it.format() }
                        if (requestChapter.contentError) {
                            requestChapter.title = chapter.title + "(章节内容错误)"
                        } else {
                            // 重试成功后要把标题恢复回来
                            requestChapter.title = chapter.title
                        }
                        if (loader.pageStatus == STATUS_LOADING && loader.chapterPos == requestChapter.chapterIndex) {
                            loader.openChapter()
                        } else if (loader.pageStatus == STATUS_FINISH && loader.chapterPos == requestChapter.chapterIndex - 1) {
                            loader.preLoadNextChapter()
                        }
                        // 当前章加载完就预加载下一章（只 1 章）
                        if (requestChapter.chapterIndex == loader.chapterPos) {
                            prefetchNextChapter(requestChapter.chapterIndex)
                        }
                    }
                }
            }

            override fun onBookRecordChange(bean: BookRecordBean) {
                viewModel.saveRecord(bean)
            }
        })
    }

    /**
     * 加载章节正文：重试策略在加载层（[NovelChapterLoader]），这里只同步阅读页的章节状态。
     *
     * 失败时把 [TxtChapter.contentError] 置起来，阅读页下次解析章节时会再次请求这一章。
     *
     * @return 是否加载成功
     */
    private suspend fun loadChapterContentWithRetry(chapter: Chapter, txtChapter: TxtChapter): Boolean {
        val ok = viewModel.getChapterContent(chapter)
        txtChapter.contentError = !ok
        if (!ok) Log.e("章节加载失败（加载层已重试）：${chapter.title}")
        return ok
    }

    /**
     * 预加载下一章（只 1 章）。
     *
     * - 失败不阻塞当前阅读：加载层内部会重试 3 次；
     * - 仍失败时，翻到下一章 PageLoader 发现没内容会再次请求（翻页重试）；
     * - 内容就绪后顺手预排版，翻页可直接显示。
     */
    private fun prefetchNextChapter(currentIndex: Int) {
        // 阅读页不在前台时不做预加载（朗读服务会按自己的需要预加载）
        if (!pageVisible) return
        val loader = mPageLoader ?: return
        val nextIndex = currentIndex + 1
        val txtChapter = loader.chapterCategory?.getOrNull(nextIndex) ?: return
        if (txtChapter.content != null && !txtChapter.contentError) return
        val chapter = viewModel.book.value?.chapterList?.getOrNull(nextIndex) ?: return
        launch("prefetchNextChapter") {
            if (loadChapterContentWithRetry(chapter, txtChapter)) {
                txtChapter.title = chapter.title
                txtChapter.content = chapter.content?.joinToString("\n") { it.format() }
                mPageLoader?.preLoadNextChapter()
            }
        }
    }

    /**
     * 跟随朗读进度：切章、翻页并高亮当前朗读段落
     */
    private fun onTtsStateChanged(state: TtsPlaybackState) {
        val loader = mPageLoader ?: return
        // 朗读的是别的书/网页时不跟随，避免把当前书切走
        val following = state.active && state.mode == TtsPlaybackMode.BOOK && state.bookId == bookId
        if (!following || state.chapterIndex < 0) {
            ttsFollowJob?.cancel()
            // 只在高亮还亮着时清一次，避免进度刷新时反复重绘
            if (ttsFollowKey.isNotEmpty()) {
                ttsFollowKey = ""
                loader.setTtsSpeakLine(null)
            }
            return
        }
        ttsFollowJob?.cancel()
        ttsFollowJob = launch {
            // 目录刷新后章节序号可能变化，优先按章节链接定位
            val targetIndex = resolveTtsChapterIndex(state)
            if (targetIndex < 0) return@launch
            if (loader.chapterPos != targetIndex) {
                Log.i("朗读跟随切章：${loader.chapterPos} -> $targetIndex")
                loader.skipToChapter(targetIndex)
                // 等目标章节排版完成再定位
                var wait = 0
                while (wait++ < MAX_TTS_CHAPTER_WAIT &&
                    (loader.chapterPos != targetIndex || loader.curPageList.isNullOrEmpty())
                ) {
                    delay(100)
                }
            }
            // 服务端正文还没加载完（段落数未知）：先不动视图，
            // 否则会按旧的段落位置先跳一次再跳回来（跨页段落启动朗读时表现为翻回上一页闪一下）
            if (state.paragraphCount <= 0) return@launch
            // 按「段落序号 + 段内进度」定位到具体行，实现按行高亮
            // 起始段跨页时带起点偏移，高亮从本页第一行开始，不会翻回上一页
            val located = TtsChapterSplitter.locateLineInParagraph(
                loader.curPageList,
                state.paragraphIndex,
                state.paragraphProgress,
                state.paragraphStartOffset
            ) ?: TtsChapterSplitter.locateByParagraphText(loader.curPageList, state.paragraphText)
                ?.let { LocatedLine(it.first, it.second, 0) }
            if (located == null) {
                // 段落序号对不上（正文被替换规则改写过等），放弃本次高亮，不影响朗读
                Log.i("朗读跟随定位失败：第${state.paragraphIndex + 1}段")
                return@launch
            }
            // 位置（章节 + 段落 + 段内行）没变就不重绘
            val key = "${state.chapterUrl}:${state.paragraphIndex}:${located.indexInParagraph}"
            if (key == ttsFollowKey) return@launch
            ttsFollowKey = key
            loader.setTtsSpeakLine(located.line)
            if (located.pagePos != loader.pagePos) {
                loader.skipToPage(located.pagePos)
            }
        }
    }

    private fun resolveTtsChapterIndex(state: TtsPlaybackState): Int {
        val chapterList = viewModel.chapterList.value
        if (!chapterList.isNullOrEmpty() && state.chapterUrl.isNotBlank()) {
            val index = chapterList.indexOfFirst { it.url == state.chapterUrl }
            if (index >= 0) return index
        }
        return state.chapterIndex
    }

    /**
     * Android 13+ 通知需要运行时权限：没有权限也能后台朗读，只是看不到通知栏控制
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        toast("未授予通知权限，朗读仍可在后台继续，但通知栏无法显示播放控制")
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_CODE_NOTIFICATION
        )
    }

    private fun initData() {
        launch(singleCoroutineKey = "initBookReaderData") {
            ChapterPageCache.resetId(bookId)
            TxtChapter.evictAll()
            mPageLoader?.closeBook()
            Log.i("加载书籍：${bookId}")
            val book = viewModel.init(bookId)
            if (book == null) {
                Log.i("书籍不存在：${bookId}")
                toast("书籍不存在")
                finish()
                return@launch
            }

            Log.i("阅读记录 ${book.record}")
            Log.i("加载章节列表")

            if (book.chapterList.isNullOrEmpty()) {
                binding!!.pageView.showSnackbar("正在加载书籍信息,请稍后……")
                viewModel.reloadBookFromNet()
                binding!!.pageView.showSnackbar("加载完成")
                if (book.chapterList.isNullOrEmpty()) {
                    mPageLoader?.chapterError()
                }
            }

            book.record?.also { record ->
                Log.i("设置阅读记录")
                mPageLoader?.setBookRecord(BookRecordBean().apply {
                    bookId = book.id
                    chapter = if (record.isEnd) record.chapterIndex + 1 else record.chapterIndex
                    val lastChapterIndex = book.chapterList?.lastIndex ?: 0
                    chapter = min(max(chapter, 0), lastChapterIndex)
                    pagePos = if (record.isEnd && lastChapterIndex > record.chapterIndex) 0 else record.offest
                    scrollOffset = if (record.isEnd && lastChapterIndex > record.chapterIndex) 0 else record.scrollOffset
                    isEnd = record.isEnd
                })
            }
            mPageLoader?.mCollBook = book
            mPageLoader?.chapterCategory = book.chapterList?.map { chapter ->
                TxtChapter().apply {
                    this.bookId = book.url
                    this.link = chapter.url
                    this.title = chapter.title
                    this.chapterIndex = chapter.index
                }
            }
            Log.i("刷新章节信息，章节数：${book.chapterList?.size}")
            mPageLoader?.setReplacementRules(ReplacementRuleUseCase.getEnabledRules())
            mPageLoader?.refreshChapterList()
            getSettingFragment()?.refreshChapterProgress()
            // 重新打开书籍后页码/排版都是新的，重置跟随位置并立即定位到正在朗读的段落
            ttsFollowKey = ""
            TtsPlaybackController.state.value?.takeIf { it.active }?.let { onTtsStateChanged(it) }
            // 从朗读播放卡片左侧按钮进来：直接唤起菜单（里面是播放卡片）
            handleShowTtsPlayCard()
        }
    }

    private fun applyReaderOrientation(mode: Int) {
        requestedOrientation = when (mode) {
            0 -> ActivityInfo.SCREEN_ORIENTATION_USER
            1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            3 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_USER
        }
    }

    private fun applyReaderBrightness() {
        val attrs = window.attributes ?: return
        val percent = globalConfig.readerBrightnessPercent.value ?: -1
        attrs.screenBrightness = if (percent < 0) {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        } else {
            percent.coerceIn(1, 255) / 255f
        }
        window.attributes = attrs
    }

}
