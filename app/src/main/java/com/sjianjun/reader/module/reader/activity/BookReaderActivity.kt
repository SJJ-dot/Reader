package com.sjianjun.reader.module.reader.activity

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.databinding.ActivityBookReaderBinding
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.event.observe
import com.sjianjun.reader.module.main.ChapterListFragment
import com.sjianjun.reader.module.reader.BookReaderSettingFragment
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.TtsUtil
import com.sjianjun.reader.utils.dp2Px
import com.sjianjun.reader.utils.fragmentCreate
import com.sjianjun.reader.utils.showSnackbar
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import sjj.alog.Log
import sjj.novel.view.reader.bean.BookBean
import sjj.novel.view.reader.bean.BookRecordBean
import sjj.novel.view.reader.page.ChapterPageCache
import sjj.novel.view.reader.page.CustomPageStyle
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

class BookReaderActivity : BaseActivity() {
    var binding: ActivityBookReaderBinding? = null
    private val TAG_SETTING_DIALOG = "BookReaderSettingFragment"
    private val bookId get() = intent.getStringExtra(BOOK_ID)!!

    private val ttsUtil by viewModels<TtsUtil>()
    private val mPageLoader get() = binding?.pageView?.pageLoader
    private val viewModel by viewModels<BookReaderViewModel>()

    private var chapterCacheJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookReaderBinding.inflate(layoutInflater)
        val pageStyle = PageStyle.getStyle(globalConfig.readerPageStyle.value)
        enableEdgeToEdge(pageStyle.isDark)
        setContentView(binding!!.root)
        // 保持屏幕常亮（阅读时屏幕不自动关闭）
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ViewCompat.setOnApplyWindowInsetsListener(binding!!.root) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            mPageLoader?.mDisplayParams?.navigationBarHeight = navigationBars.bottom
            mPageLoader?.mDisplayParams?.statusBarHeight = statusBars.top
            binding?.drawerChapterList?.setPadding(0, statusBars.top, 0, navigationBars.bottom)
            insets
        }

        initSettingMenu()
        Log.i("BookReaderActivity onCreate savedInstanceState: $savedInstanceState")
        initData()
        initView()
        initBackPressed()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.i("onNewIntent: ${intent?.getStringExtra(BOOK_ID)}")
        initData()
    }

    fun initBackPressed() {
        var lastTime = 0L
        setOnBackPressed {
            val fragment = supportFragmentManager.findFragmentByTag(TAG_SETTING_DIALOG)
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
        ttsUtil.progressChangeCallback = { textLine ->
            launch {
                val loader = mPageLoader ?: return@launch
                val page = loader.curPageList?.find { page ->
                    page.lines.find { textLine == it } != null
                }
                loader.setTtsSpeakLine(textLine)
                if (page != null && page.position != loader.pagePos) {
                    loader.skipToPage(page.position)
                }
            }
        }
        ttsUtil.onCompleted = {
//            播放下一个段落
            mPageLoader?.setTtsSpeakLine(null)
            if (mPageLoader?.skipNextChapter() == true) {
                EventBus.post(EventKey.CHAPTER_SPEAK)
            }
        }
        observe<String>(EventKey.CHAPTER_SPEAK) {
            launch {
                if (ttsUtil.isSpeaking.value == true) {
                    ttsUtil.stop()
                    mPageLoader?.setTtsSpeakLine(null)
                    return@launch
                }
                if (mPageLoader?.pageStatus == STATUS_LOADING) {
                    toast("书籍正在加载中")
                    return@launch
                }
                val chapter = viewModel.chapterList.value?.getOrNull(mPageLoader?.chapterPos ?: 0)
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
                ttsUtil.start(mPageLoader?.curPageList, mPageLoader?.pagePos ?: 0)
            }
        }

        observe<String>(EventKey.CHAPTER_LIST_CAHE) {
            if (chapterCacheJob?.isActive == true) {
                chapterCacheJob?.cancel()
                viewModel.chapterCache.postValue(false)
                toast("已取消章节缓存")
                return@observe
            }
            chapterCacheJob = launch("CHAPTER_LIST_CAHE") {
                val chapterList = viewModel.chapterList.value ?: return@launch
                viewModel.chapterCache.postValue(true)
                val end = chapterList.size
                val start = max(0, mPageLoader?.chapterPos ?: 0)
                toast("章节缓存：开始")
                (start until end).forEach {
                    viewModel.getChapterContent(chapterList[it])
                    delay(100)
                    ensureActive()
                }
                viewModel.chapterCache.postValue(false)
                toast("章节缓存：完成")
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
            BrowserReaderActivity.startActivity(this, url)
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
                    getChapterContentPage(curChapter, chapter)
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
                getChapterContentPage(txtChapter, chapter)

            }
        }

        globalConfig.readerPageMode.observe(this) {
            mPageLoader?.setPageMode(PageMode.entries.getOrNull(it) ?: PageMode.SIMULATION)
        }
        globalConfig.readerBrightnessMaskColor.observe(this) {
            binding!!.brightnessMask.setBackgroundColor(it)
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

        binding!!.pageView.setTouchListener(object : PageView.TouchListener {
            override fun intercept(event: MotionEvent?): Boolean {
                //隐藏设置对话框
                return supportFragmentManager.findFragmentByTag(TAG_SETTING_DIALOG)?.let {
                    if (it.isVisible && event?.action == MotionEvent.ACTION_UP) {
                        supportFragmentManager.beginTransaction()
                            .hide(it)
                            .commitAllowingStateLoss()
                    }
                    return@let it.isVisible

                } ?: false
            }

            override fun center() {
                //显示设置对话框
                supportFragmentManager.findFragmentByTag(TAG_SETTING_DIALOG)?.let {
                    supportFragmentManager.beginTransaction()
                        .show(it)
                        .commitAllowingStateLoss()
                }
            }

        })
    }

    private fun initView() {
        val settingDialog = supportFragmentManager.findFragmentByTag(TAG_SETTING_DIALOG)
        if (settingDialog == null) {
            val fragment = BookReaderSettingFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.fl_setting_container, fragment, TAG_SETTING_DIALOG)
                .hide(fragment)
                .commitAllowingStateLoss()
        }
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

                launch("requestChapters") {
                    requestChapters.forEach { requestChapter ->
                        val chapter = viewModel.book.value?.chapterList?.getOrNull(requestChapter.chapterIndex) ?: return@launch
                        viewModel.getChapterContent(chapter)
                        requestChapter.content = chapter.content?.joinToString("\n") { it.format() }
                        if (chapter.content?.firstOrNull()?.contentError == true) {
                            requestChapter.title = chapter.title + "(章节内容错误)"
                        }
                        if (loader.pageStatus == STATUS_LOADING && loader.chapterPos == requestChapter.chapterIndex) {
                            loader.openChapter()
                        } else if (loader.pageStatus == STATUS_FINISH && loader.chapterPos == requestChapter.chapterIndex - 1) {
                            loader.preLoadNextChapter()
                        }
                        getChapterContentPage(requestChapter, chapter)
                    }
                }
            }

            override fun onBookRecordChange(bean: BookRecordBean) {
                viewModel.saveRecord(bean)
            }
        })
    }

    private fun initData() {
        launch(singleCoroutineKey = "initBookReaderData") {
            ChapterPageCache.resetId(bookId)
//            TxtChapter.evictAll()
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
                    isEnd = record.isEnd
                })
            }
            Log.i("设置阅读器内容")
            Log.i("设置书籍信息")
            mPageLoader?.mCollBook = BookBean().apply {
                id = book.url
                title = book.title
                author = book.author
                shortIntro = book.intro
                cover = book.cover
                bookChapterList = book.chapterList?.map { chapter ->
                    TxtChapter().apply {
                        this.bookId = book.url
                        this.link = chapter.url
                        this.title = chapter.title
                        this.chapterIndex = chapter.index
                    }
                }

            }
            Log.i("刷新章节信息，章节数：${book.chapterList?.size}")
            mPageLoader?.refreshChapterList()
            val settingDialog = supportFragmentManager.findFragmentByTag(TAG_SETTING_DIALOG) as? BookReaderSettingFragment
            settingDialog?.refreshChapterProgress()
        }
    }

    private suspend fun getChapterContentPage(txtChapter: TxtChapter?, chapter: Chapter) = withMain {
        val launch = launch("getChapterContentPage") {
            val loader = mPageLoader ?: return@launch
            val txtChapter = txtChapter ?: return@launch
            while (true) {
                ensureActive()
                if (viewModel.getChapterContentPage(chapter)) {
                    if (loader.chapterPos in txtChapter.chapterIndex - 3..txtChapter.chapterIndex + 3) {
                        txtChapter.content = chapter.content?.joinToString("\n") { it.format() }
                    }
                    if (loader.chapterPos in txtChapter.chapterIndex..txtChapter.chapterIndex + 1 && loader.pageStatus == STATUS_FINISH) {
                        loader.reloadPages()
                    }
                } else {
                    break
                }
            }
        }
        launch.join()

    }


}
