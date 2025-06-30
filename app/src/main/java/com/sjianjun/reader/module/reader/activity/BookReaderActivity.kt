package com.sjianjun.reader.module.reader.activity

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.MotionEvent
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.distinctUntilChanged
import com.gyf.immersionbar.ImmersionBar
import com.sjianjun.coroutine.launch
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.BOOK_ID
import com.sjianjun.reader.BOOK_TITLE
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.databinding.ActivityBookReaderBinding
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.event.observe
import com.sjianjun.reader.module.main.ChapterListFragment
import com.sjianjun.reader.module.reader.BookReaderSettingFragment
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.TtsUtil
import com.sjianjun.reader.utils.dp2Px
import com.sjianjun.reader.utils.fragmentCreate
import com.sjianjun.reader.utils.showSnackbar
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import sjj.alog.Log
import sjj.novel.view.reader.bean.BookBean
import sjj.novel.view.reader.bean.BookRecordBean
import sjj.novel.view.reader.page.CustomPageStyle
import sjj.novel.view.reader.page.PageLoader
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
    private var book: Book? = null
    private lateinit var readingRecord: ReadingRecord

    private val ttsUtil by lazy { TtsUtil(this, lifecycle) }
    private val mPageLoader by lazy { binding?.pageView!!.pageLoader }
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
                val chapter = book?.chapterList?.getOrNull(mPageLoader.chapterPos)
                if (chapter == null) {
                    toast("当前章节获取失败")
                    return@launch
                }
                if (!chapter.isLoaded || chapter.content == null) {
                    toast("章节未加载成功 $chapter")
                    return@launch
                }
                if (chapter.content?.contentError == true) {
                    toast("章节内容错误 $chapter")
                    return@launch
                }
                ttsUtil.start(mPageLoader.curPageList, mPageLoader.pagePos)
            }
        }

        observe<String>(EventKey.CHAPTER_LIST_CAHE) {
            launch("CHAPTER_LIST_CAHE") {
                showSnackbar(binding!!.pageView, "章节缓存：${0}/${(book?.chapterList?.size ?: 0)}")
                var first = 3
                (max(0, mPageLoader.chapterPos) until (book?.chapterList?.size ?: 0)).forEach {
                    getChapterContent(book?.chapterList?.get(it))
                    if (first-- > 0) {
                        showSnackbar(binding!!.pageView, "章节缓存：${it}/${(book?.chapterList?.size ?: 0)}")
                    }
                }
                showSnackbar(binding!!.pageView, "章节缓存：完成")
            }
        }
        observe<String>(EventKey.CHAPTER_LIST) {
            binding?.drawerLayout!!.openDrawer(GravityCompat.END)
        }
        observe<String>(EventKey.BROWSER_OPEN) {
            //使用浏览器打开书籍链接
            val chapterIdx = mPageLoader.curChapter?.chapterIndex ?: -1
            val txtChapter = book?.chapterList?.getOrNull(chapterIdx)
            if (txtChapter == null) {
                toast("当前章节获取失败")
                return@observe
            }
            BrowserReaderActivity.startActivity(this, txtChapter.url)
        }
        observe<String>(EventKey.CHAPTER_SYNC_FORCE) {
            launch("CHAPTER_SYNC_FORCE") {
                if (mPageLoader.pageStatus == STATUS_LOADING) {
                    return@launch
                }
                mPageLoader.pageStatus = STATUS_LOADING

                mPageLoader.curChapter?.let { curChapter ->
                    val txtChapter =
                        book?.chapterList?.getOrNull(curChapter.chapterIndex) ?: return@launch
                    toast("正在加载中，请稍候……")
                    val chapter = DataManager.getChapterContent(txtChapter, 1)
                    curChapter.content = chapter.content?.format().toString()
                    curChapter.title = chapter.title
                    mPageLoader.refreshChapter(curChapter)
                    toast("加载完成")
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
                val chapter = book?.chapterList?.getOrNull(curChapter)
                if (chapter == null) {
                    toast("当前章节获取失败")
                    return@launch
                }
                if (!chapter.isLoaded || chapter.content == null) {
                    toast("章节未加载成功 $chapter")
                    return@launch
                }
                if (chapter.content?.contentError != false) {
                    chapter.content?.contentError = false
                    txtChapter?.title = chapter.title
                    toast("已取消标记章节内容错误")
                } else {
                    chapter.content?.contentError = true
                    txtChapter?.title = chapter.title + "(章节内容错误)"
                    toast("已标记章节内容错误")
                }
                val pagePos = mPageLoader.pagePos
                mPageLoader.skipToChapter(curChapter)
                mPageLoader.skipToPage(pagePos)
                DataManager.insertChapterContent(chapter.content!!)
            }
        }

        globalConfig.readerPageMode.observe(this) {
            mPageLoader.setPageMode(PageMode.values()[it])
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

    private fun initData() {
        launch(singleCoroutineKey = "initBookReaderData") {
            Log.i("加载书籍：${bookId}")
            val book = DataManager.getBookById(bookId)
            if (book == null) {
                Log.i("书籍不存在：${bookId}")
                toast("书籍不存在")
                finish()
                return@launch
            }
            this@BookReaderActivity.book = book
            Log.i("设置章节列表 ChapterListFragment")
            val fragment = supportFragmentManager.findFragmentByTag(book.title)
            if (fragment != null && fragment is ChapterListFragment) {
                Log.i("章节列表已存在，直接使用")
            } else {
                Log.i("章节列表不存在，创建新的")
                supportFragmentManager.beginTransaction()
                    .replace(R.id.drawer_chapter_list, fragmentCreate<ChapterListFragment>(BOOK_TITLE to book.title), book.title)
                    .commitAllowingStateLoss()
            }
            readingRecord = DataManager.getReadingRecord(book).first()
                ?: ReadingRecord(book.title)
            readingRecord.bookId = bookId
            Log.i("阅读记录 $readingRecord")
            Log.i("加载章节列表")
            var chapterList = DataManager.getChapterList(bookId).first()
            book.chapterList = chapterList
            if (chapterList.isEmpty()) {
                showSnackbar(binding!!.pageView, "正在加载书籍信息,请稍后……")
                DataManager.reloadBookFromNet(book)
                showSnackbar(binding!!.pageView, "加载完成")
                chapterList = DataManager.getChapterList(bookId).first()
                book.chapterList = chapterList
                if (chapterList.isEmpty()) {
                    mPageLoader.chapterError()
                }
            }

            readingRecord.also {
                Log.i("设置阅读记录")
                mPageLoader.setBookRecord(BookRecordBean().apply {
                    bookId = book.id
                    chapter = if (it.isEnd) it.chapterIndex + 1 else it.chapterIndex
                    chapter = min(max(chapter, 0), chapterList.lastIndex)
                    pagePos =
                        if (it.isEnd && chapterList.lastIndex > it.chapterIndex) 0 else it.offest
                    isEnd = it.isEnd
                })
            }
            Log.i("设置阅读器内容")
            mPageLoader.setOnPageChangeListener(object : PageLoader.OnPageChangeListener {

                override fun requestChapterPage(chapter: TxtChapter) {

                }

                override fun requestChapters(requestChapters: MutableList<TxtChapter>) {
                    Log.i("加载章节内容 $requestChapters")
                    requestChapters.forEach { requestChapter ->
                        launch {
                            val chapter = book.chapterList?.getOrNull(requestChapter.chapterIndex) ?: return@launch
                            if (getChapterContent(chapter)) {
                                requestChapter.content = chapter.content?.format()
                                if (chapter.content?.contentError == true) {
                                    requestChapter.title = chapter.title + "(章节内容错误)"
                                }
                                if (mPageLoader.pageStatus == STATUS_LOADING && mPageLoader.chapterPos == requestChapter.chapterIndex) {
                                    mPageLoader.openChapter()
                                }
                            } else {
                                if (mPageLoader.pageStatus == STATUS_LOADING && mPageLoader.chapterPos == requestChapter.chapterIndex) {
                                    mPageLoader.chapterError()
                                }
                            }
                        }
                    }
                }

                override fun onBookRecordChange(bean: BookRecordBean) {

                    if (bean.chapter != readingRecord.chapterIndex ||
                        bean.pagePos != readingRecord.offest ||
                        bean.isEnd != readingRecord.isEnd
                    ) {
                        Log.i("保存阅读记录 $bean")
                        launch("saveReadingRecord") {
                            readingRecord.chapterIndex = bean.chapter
                            readingRecord.offest = bean.pagePos
                            readingRecord.isEnd = bean.isEnd
                            readingRecord.updateTime = System.currentTimeMillis()
                            DataManager.setReadingRecord(readingRecord)
                        }
                    }

                }
            })
            initBookData(book)
        }
    }

    private fun initBookData(book: Book) {
        Log.i("设置书籍信息")
        mPageLoader.closeBook()
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

    /**
     * 加载 上一章 当前章 下一章
     */
    private suspend fun getChapterContent(
        chapter: Chapter?
    ) = withIo {
        chapter ?: return@withIo false

        while (chapter.isLoading.get()) {
            delay(100)
        }

        if (chapter.isLoaded && chapter.content != null) {
            return@withIo false
        }

        if (!chapter.isLoading.compareAndSet(false, true)) {
            return@withIo false
        }

        try {
            //force
            DataManager.getChapterContent(chapter)
        } finally {
            chapter.isLoading.set(false)
        }

        return@withIo true
    }

}
