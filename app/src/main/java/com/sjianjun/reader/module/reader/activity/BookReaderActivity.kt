package com.sjianjun.reader.module.reader.activity

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.lifecycle.MediatorLiveData
import com.gyf.immersionbar.ImmersionBar
import com.sjianjun.coroutine.launch
import com.sjianjun.coroutine.launchIo
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.event.observe
import com.sjianjun.reader.module.main.ChapterListFragment
import com.sjianjun.reader.module.reader.BookReaderSettingFragment
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.*
import kotlinx.android.synthetic.main.activity_book_reader.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import sjj.alog.Log
import sjj.novel.view.reader.bean.BookBean
import sjj.novel.view.reader.bean.BookRecordBean
import sjj.novel.view.reader.page.*
import sjj.novel.view.reader.page.PageLoader.STATUS_LOADING
import kotlin.math.max
import kotlin.math.min

class BookReaderActivity : BaseActivity() {
    private val TAG_SETTING_DIALOG = "BookReaderSettingFragment"
    private val bookId get() = intent.getStringExtra(BOOK_ID)!!
    private var book: Book? = null
    private val chapterIndex get() = (intent.getStringExtra(CHAPTER_INDEX) ?: "-1").toInt()
    private lateinit var readingRecord: ReadingRecord

    //    private val adapter by lazy { ContentAdapter(this) }
    private val ttsUtil by lazy { TtsUtil(this, lifecycle) }
    private val mPageLoader by lazy { page_view.pageLoader }
    override fun immersionBar() {
//        val dark = globalConfig.appDayNightMode != AppCompatDelegate.MODE_NIGHT_YES
        ImmersionBar.with(this).init()
//            .hideBar(BarHide.FLAG_HIDE_STATUS_BAR)
//            .init()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_reader)

        content?.setPadding(0, ImmersionBar.getStatusBarHeight(this), 0, 0)
        initSettingMenu()
        initData()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (this::readingRecord.isInitialized && readingRecord.bookId == bookId) {
            mPageLoader.skipToChapter(chapterIndex)
        } else {
            initData()
        }
    }

    override fun onPause() {
        super.onPause()
        mPageLoader.saveRecord()
    }

    override fun onBackPressed() {
        when {
            drawer_layout.isDrawerOpen(GravityCompat.END) -> {
                drawer_layout.closeDrawer(GravityCompat.END)
            }
            drawer_layout.isDrawerOpen(GravityCompat.START) -> {
                drawer_layout.closeDrawer(GravityCompat.START)
            }
            else -> {
                super.onBackPressed()
            }
        }
    }

    private fun initSettingMenu() {
        observe<String>(EventKey.CHAPTER_SYNC_FORCE) {
            launch("CHAPTER_SYNC_FORCE") {
                if (mPageLoader.pageStatus == STATUS_LOADING) {
                    return@launch
                }
                mPageLoader.pageStatus = STATUS_LOADING
                val curChapter = mPageLoader.curChapter
                val txtChapter =
                    book?.chapterList?.getOrNull(curChapter.chapterIndex) ?: return@launch
                toast("正在加载中，请稍候……")
                val chapter = DataManager.getChapterContent(txtChapter, true)
                curChapter.content = chapter.content?.format().toString()
                mPageLoader.refreshChapter(curChapter)
                toast("加载完成")
            }
        }

        globalConfig.readerPageMode.observe(this) {
            mPageLoader.setPageMode(PageMode.values()[it])
        }
        globalConfig.readerBrightnessMaskColor.observe(this) {
            brightness_mask.setBackgroundColor(it)
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
        text.observe(this) {
            Log.i("设置字号:${it.first} 行间距：${it.second}")
            mPageLoader.setTextSize(it.first.dp2Px, it.second)
        }

        globalConfig.readerPageStyle.observe(this) {
            val pageStyle = PageStyle.getStyle(it)
            content.background = pageStyle.getBackground(this)
//            line.setBackgroundColor(pageStyle.getSpacerColor(this))
//            chapter_title.setTextColor(pageStyle.getLabelColor(this))
            if (pageStyle.isDark || pageStyle == PageStyle.STYLE_0 && globalConfig.appDayNightMode == AppCompatDelegate.MODE_NIGHT_YES) {
                ImmersionBar.with(this).statusBarDarkFont(false).init()
            } else {
                ImmersionBar.with(this).statusBarDarkFont(true).init()
            }
            Log.i("pageStyle:${pageStyle}")
            mPageLoader.setPageStyle(pageStyle)
        }

        page_view.setTouchListener(object : PageView.TouchListener {
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
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.drawer_chapter_list,
                    fragmentCreate<ChapterListFragment>(
                        BOOK_TITLE to book.title,
                        BOOK_AUTHOR to book.author
                    )
                )
                .commitAllowingStateLoss()
            readingRecord = DataManager.getReadingRecord(book).first()
                ?: ReadingRecord(book.title, book.author)
            Log.i("修正阅读记录 $readingRecord")
            readingRecord.bookId = bookId
            if (chapterIndex != -1) {
                readingRecord.chapterIndex = chapterIndex
                readingRecord.offest = 0
                readingRecord.isEnd = false
                DataManager.setReadingRecord(readingRecord)
            }
            Log.i("修正阅读记录 $readingRecord")
            Log.i("加载章节列表")
            var chapterList = DataManager.getChapterList(bookId).first()
            book.chapterList = chapterList
            if (chapterList.isEmpty()) {
                showSnackbar(page_view, "正在加载书籍信息,请稍后……")
                DataManager.reloadBookFromNet(book)
                showSnackbar(page_view, "加载完成")
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
                override fun onChapterChange(pos: Int) {
                }

                override fun requestChapters(requestChapters: MutableList<TxtChapter>) {
                    Log.i("加载章节内容 $requestChapters")
                    launchIo {
                        val list = requestChapters.mapNotNull {
                            book.chapterList?.getOrNull(it.chapterIndex)
                        }
//                        assert(list.size != requestChapters.size)
                        if (getChapterContent(list)) {
                            list.forEach { chapter ->
                                val txtChapter =
                                    requestChapters.find { it.chapterIndex == chapter.index }
                                txtChapter?.content = chapter.content?.format().toString()
                            }
                            Log.i("章节内容加载结束 Status:${mPageLoader.pageStatus}")
                            if (mPageLoader.pageStatus == STATUS_LOADING) {
                                mPageLoader.openChapter()
                            }
                        } else {
                            Log.i("章节内容加载失败")
                            //这里应该来不了
                            if (mPageLoader.pageStatus == STATUS_LOADING) {
                                mPageLoader.chapterError()
                            }
                        }

                    }

                }

                override fun onCategoryFinish(chapters: MutableList<TxtChapter>?) {
                }

                override fun onPageCountChange(count: Int) {
                }

                override fun onPageChange(pos: Int) {
                }

                override fun onBookRecordChange(bean: BookRecordBean) {
                    launchIo {
                        Log.i("保存阅读记录 ${bean}")
                        readingRecord.chapterIndex = bean.chapter
                        readingRecord.offest = bean.pagePos
                        readingRecord.isEnd = bean.isEnd
                        DataManager.setReadingRecord(readingRecord)
                    }
                }
            })
            initBookData(book)
        }
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

    /**
     * 加载 上一章 当前章 下一章
     */
    private suspend fun getChapterContent(
        chapters: List<Chapter>
    ) = withIo {
        if (chapters.isEmpty()) {
            return@withIo false
        }
        val list = chapters.map { chapter ->
            async {
                if (chapter.isLoaded && chapter.content != null) {
                    return@async false
                }

                if (!chapter.isLoading.compareAndSet(false, true)) {
                    return@async false
                }

                try {
                    //force
                    DataManager.getChapterContent(chapter)
                } finally {
                    chapter.isLoading.lazySet(false)
                }
            }
        }.awaitAll()


        return@withIo true
    }

}
