package com.sjianjun.reader.module.reader.activity

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.viewModels
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.distinctUntilChanged
import com.gyf.immersionbar.ImmersionBar
import com.sjianjun.coroutine.launch
import com.sjianjun.coroutine.withMain
import com.sjianjun.reader.BOOK_ID
import com.sjianjun.reader.BOOK_TITLE
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.Book
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

    private val ttsUtil by lazy { TtsUtil(this, lifecycle) }
    private val mPageLoader by lazy { binding?.pageView!!.pageLoader }
    private val viewModel by viewModels<BookReaderViewModel>()
    override fun immersionBar() {
        ImmersionBar.with(this).init()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookReaderBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        binding?.readerRoot?.setPadding(0, ImmersionBar.getStatusBarHeight(this), 0, 0)
        initSettingMenu()
        Log.i("BookReaderActivity onCreate savedInstanceState: $savedInstanceState")
        initData()
        initView()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.i("onNewIntent: ${intent?.getStringExtra(BOOK_ID)}")
        initData()
    }

    override fun onBackPressed() {
        when {
            binding?.drawerLayout!!.isDrawerOpen(GravityCompat.END) -> {
                binding?.drawerLayout!!.closeDrawer(GravityCompat.END)
            }

            binding?.drawerLayout!!.isDrawerOpen(GravityCompat.START) -> {
                binding?.drawerLayout!!.closeDrawer(GravityCompat.START)
            }

            else -> {
                super.onBackPressed()
            }
        }
    }

    private fun initSettingMenu() {
        ttsUtil.progressChangeCallback = { paragraph ->
            launch {
                val page = mPageLoader.curPageList?.find { page ->
                    page.lines.find { paragraph.first() == it } != null
                }
                if (page != null) {
                    mPageLoader.skipToPage(page.position)
                }
            }
        }
        ttsUtil.onCompleted = {
//            播放下一个段落
            if (mPageLoader.skipNextChapter()) {
                EventBus.post(EventKey.CHAPTER_SPEAK)
            }
        }
        observe<String>(EventKey.CHAPTER_SPEAK) {
            launch {
                if (ttsUtil.isSpeaking) {
                    ttsUtil.stop()
                    return@launch
                }
                if (mPageLoader.pageStatus == STATUS_LOADING) {
                    toast("书籍正在加载中")
                    return@launch
                }
                val chapter = viewModel.chapterList.value?.getOrNull(mPageLoader.chapterPos)
                if (chapter == null) {
                    toast("当前章节获取失败")
                    return@launch
                }
                if (!chapter.isLoaded || chapter.content == null) {
                    toast("章节未加载成功 $chapter")
                    return@launch
                }
                if (chapter.content?.firstOrNull()?.contentError == true) {
                    toast("章节内容错误 $chapter")
                    return@launch
                }
                ttsUtil.start(mPageLoader.curPageList, mPageLoader.pagePos)
            }
        }

        observe<String>(EventKey.CHAPTER_LIST_CAHE) {
            launch("CHAPTER_LIST_CAHE") {
                val chapterList = viewModel.chapterList.value ?: return@launch
                binding!!.pageView.showSnackbar("章节缓存：${0}/${chapterList.size}")
                var first = 3
                (max(0, mPageLoader.chapterPos) until chapterList.size).forEach {
                    viewModel.getChapterContent(chapterList[it])
                    ensureActive()
                    if (first-- > 0) {
                        binding!!.pageView.showSnackbar("章节缓存：${it}/${chapterList.size}")
                    }
                }
                binding!!.pageView.showSnackbar("章节缓存：完成")
            }
        }
        observe<String>(EventKey.CHAPTER_LIST) {
            binding?.drawerLayout!!.openDrawer(GravityCompat.END)
        }
        observe<String>(EventKey.BROWSER_OPEN) {
            //使用浏览器打开书籍链接
            val chapterIdx = mPageLoader.curChapter?.chapterIndex ?: -1
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
                if (mPageLoader.pageStatus == STATUS_LOADING) {
                    return@launch
                }
                mPageLoader.pageStatus = STATUS_LOADING

                mPageLoader.curChapter?.let { curChapter ->
                    val chapter = viewModel.chapterList.value?.getOrNull(curChapter.chapterIndex) ?: return@launch
                    toast("正在加载中，请稍候……")
                    viewModel.getChapterContent(chapter, 1)
                    curChapter.content = chapter.content?.joinToString("\n") { it.format() }
                    if (chapter.content?.firstOrNull()?.contentError == true) {
                        curChapter.title = chapter.title + "(章节内容错误)"
                    }
                    Log.e("加载完成 ${curChapter.chapterIndex} ${curChapter.title}")
                    mPageLoader.reloadPages()
                    toast("加载完成")
                    getChapterContentPage(curChapter, chapter)
                }
            }
        }
        observe<String>(EventKey.CHAPTER_CONTENT_ERROR) {
            launch {
                if (mPageLoader.pageStatus == STATUS_LOADING) {
                    toast("书籍正在加载中")
                    return@launch
                }
                val txtChapter = mPageLoader.curChapter
                val curChapter = mPageLoader.chapterPos
                val chapter = viewModel.chapterList.value?.getOrNull(curChapter)
                if (chapter == null) {
                    toast("当前章节获取失败")
                    return@launch
                }
                if (!chapter.isLoaded || chapter.content?.isEmpty() != false) {
                    toast("章节未加载成功 $chapter")
                    return@launch
                }
                viewModel.maskChapterContentErr(chapter, txtChapter)
                val pagePos = mPageLoader.pagePos
                mPageLoader.skipToChapter(curChapter)
                mPageLoader.skipToPage(pagePos)
            }
        }

        globalConfig.readerPageMode.observe(this) {
            mPageLoader.setPageMode(PageMode.entries.getOrNull(it) ?: PageMode.SIMULATION)
        }
        globalConfig.readerBrightnessMaskColor.observe(this) {
            binding!!.brightnessMask.setBackgroundColor(it)
        }
        val text = MediatorLiveData<Pair<Int, Float>>()
        text.addSource(globalConfig.readerLineSpacing) {
            text.value =
                globalConfig.readerFontSize.value!! to globalConfig.readerLineSpacing.value!!
        }
        text.addSource(globalConfig.readerFontSize) {
            text.value =
                globalConfig.readerFontSize.value!! to globalConfig.readerLineSpacing.value!!
        }
        text.distinctUntilChanged().observe(this) {
            Log.i("设置字号:${it.first} 行间距：${it.second}")
            mPageLoader.setTextSize(it.first.dp2Px.toFloat(), it.second)
        }
        observe<CustomPageStyle>(EventKey.CUSTOM_PAGE_STYLE) {
            mPageLoader.setPageStyle(it)
            binding!!.readerRoot.background = it.getBackground(this)
            if (it.isDark) {
                ImmersionBar.with(this).statusBarDarkFont(false).init()
            } else {
                ImmersionBar.with(this).statusBarDarkFont(true).init()
            }
        }
        globalConfig.readerPageStyle.observe(this) {
            val pageStyle = PageStyle.getStyle(it)
            binding!!.readerRoot.background = pageStyle.getBackground(this)
            if (pageStyle.isDark) {
                ImmersionBar.with(this).statusBarDarkFont(false).init()
            } else {
                ImmersionBar.with(this).statusBarDarkFont(true).init()
            }
            mPageLoader.setPageStyle(pageStyle)
        }

        globalConfig.readerFontFamily.observe(this) {
            if (it.isAsset) {
                if (it.resId == 0) {
                    mPageLoader.setTypeface(null)
                } else {
                    val typeface = ResourcesCompat.getFont(this, it.resId)
                    mPageLoader.setTypeface(typeface)
                }
            } else {
                val typeface = Typeface.createFromFile(File(it.path!!))
                mPageLoader.setTypeface(typeface)
            }
        }

        binding!!.pageView.setTouchListener(object : PageView.TouchListener {
            override fun intercept(event: MotionEvent?): Boolean {
                val settingDialog = supportFragmentManager.findFragmentByTag(TAG_SETTING_DIALOG)
                settingDialog as BookReaderSettingFragment?
                val showing = settingDialog?.isVisible == true
                if (showing && event?.action == MotionEvent.ACTION_DOWN) {
                    return true
                }
                if (showing && event?.action == MotionEvent.ACTION_UP) {
                    settingDialog?.dismissAllowingStateLoss()
                    return true
                }
                return showing
            }

            override fun center() {
                showSetting()
            }

        })
    }

    private fun showSetting() {
        val fragment = BookReaderSettingFragment()
        fragment.show(supportFragmentManager, TAG_SETTING_DIALOG)
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

        mPageLoader.setOnPageChangeListener(object : PageLoader.OnPageChangeListener {

            override fun requestChapters(requestChapters: MutableList<TxtChapter>) {
                Log.i("加载章节内容 $requestChapters")

                launch("requestChapters") {
                    requestChapters.forEach { requestChapter ->
                        val chapter = viewModel.book.value?.chapterList?.getOrNull(requestChapter.chapterIndex) ?: return@launch
                        if (viewModel.getChapterContent(chapter)) {
                            requestChapter.content =
                                chapter.content?.joinToString("\n") { it.format() }
                            if (chapter.content?.firstOrNull()?.contentError == true) {
                                requestChapter.title = chapter.title + "(章节内容错误)"
                            }
                            if (mPageLoader.pageStatus == STATUS_LOADING && mPageLoader.chapterPos == requestChapter.chapterIndex) {
                                mPageLoader.openChapter()
                            } else if (mPageLoader.pageStatus == STATUS_FINISH && mPageLoader.chapterPos == requestChapter.chapterIndex - 1) {
                                mPageLoader.preLoadNextChapter()
                            }
                            getChapterContentPage(requestChapter, chapter)

                        } else {
                            if (mPageLoader.pageStatus == STATUS_LOADING && mPageLoader.chapterPos == requestChapter.chapterIndex) {
                                mPageLoader.chapterError()
                            }
                        }
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
            ChapterPageCache.reset(bookId)
            mPageLoader.closeBook()
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
                    mPageLoader.chapterError()
                }
            }

            book.record?.also { record ->
                Log.i("设置阅读记录")
                mPageLoader.setBookRecord(BookRecordBean().apply {
                    bookId = book.id
                    chapter = if (record.isEnd) record.chapterIndex + 1 else record.chapterIndex
                    val lastChapterIndex = book.chapterList?.lastIndex ?: 0
                    chapter = min(max(chapter, 0), lastChapterIndex)
                    pagePos = if (record.isEnd && lastChapterIndex > record.chapterIndex) 0 else record.offest
                    isEnd = record.isEnd
                })
            }
            Log.i("设置阅读器内容")
            initBookData(book)
        }
    }

    private suspend fun getChapterContentPage(txtChapter: TxtChapter, chapter: Chapter) = withMain {
        val launch = launch("getChapterContentPage") {
            while (true) {
                ensureActive()
                if (viewModel.getChapterContentPage(chapter)) {
                    if (mPageLoader.chapterPos in txtChapter.chapterIndex - 3..txtChapter.chapterIndex + 3) {
                        txtChapter.content = chapter.content?.joinToString("\n") { it.format() }
                    }
                    if (mPageLoader.chapterPos in txtChapter.chapterIndex..txtChapter.chapterIndex + 1 && mPageLoader.pageStatus == STATUS_FINISH) {
                        mPageLoader.reloadPages()
                    }
                } else {
                    break
                }
            }
        }
        launch.join()

    }

    private fun initBookData(book: Book) {
        Log.i("设置书籍信息")
        mPageLoader.book = BookBean().apply {
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
        mPageLoader.refreshChapterList()
    }

}
