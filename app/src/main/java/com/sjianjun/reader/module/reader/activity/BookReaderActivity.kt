package com.sjianjun.reader.module.reader.activity

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gyf.immersionbar.ImmersionBar
import com.sjianjun.coroutine.*
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.module.main.ChapterListFragment
import com.sjianjun.reader.module.reader.BookReaderSettingFragment
import com.sjianjun.reader.module.reader.style.PageStyle
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.repository.WebDavMgr
import com.sjianjun.reader.utils.*
import kotlinx.android.synthetic.main.activity_book_reader.*
import kotlinx.android.synthetic.main.reader_item_activity_chapter_content.view.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import sjj.alog.Log
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class BookReaderActivity : BaseActivity() {
    private val bookId get() = intent.getStringExtra(BOOK_ID)!!
    private val chapterIndex get() = (intent.getStringExtra(CHAPTER_INDEX) ?: "-1").toInt()
    private lateinit var readingRecord: ReadingRecord
    private val adapter by lazy { ContentAdapter(this) }
    private val ttsUtil by lazy { TtsUtil(this, lifecycle) }

    override fun immersionBar() {
        val dark = globalConfig.appDayNightMode != AppCompatDelegate.MODE_NIGHT_YES
        ImmersionBar.with(this)
            .statusBarDarkFont(dark)
            .init()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityManger.finishSameType(this)

        setContentView(R.layout.activity_book_reader)

        val params = drawer_layout.layoutParams as? ViewGroup.MarginLayoutParams
        params?.topMargin = ImmersionBar.getStatusBarHeight(this)
        recycle_view.adapter = adapter
        initSettingMenu()
        initScrollLoadChapter()
        initTTS()
        initData()
    }

    private suspend fun speak(chapter: Chapter?, start: Int) {
        chapter ?: return
        ttsUtil.speak(chapter.index, chapter.content?.format() ?: "", start)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (this::readingRecord.isInitialized && readingRecord.bookId == bookId) {
            val targetChapter = adapter.chapterList.indexOfFirst { it.index == chapterIndex }
            if (targetChapter != -1 && readingRecord.chapterIndex != chapterIndex) {
                val manager = recycle_view.layoutManager as LinearLayoutManager
                manager.scrollToPositionWithOffset(targetChapter, 0)
                recycle_view.post {
                    saveReadRecord()
                }
            }
        } else {
            initData()
        }
    }

    override fun onStop() {
        super.onStop()
        saveReadRecord()
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
        globalConfig.readerBrightnessMaskColor.observe(this, Observer {
            brightness_mask.setBackgroundColor(it)
        })
        globalConfig.readerLineSpacing.observe(this, Observer {
            adapter.notifyDataSetChanged()
        })
        globalConfig.readerFontSize.observe(this, Observer {
            adapter.notifyDataSetChanged()
        })
        globalConfig.readerPageStyle.observe(this, Observer {
            val pageStyle = PageStyle.getStyle(it)
            reader_root_background.setImageDrawable(pageStyle.getBackground(this))
            line.setBackgroundColor(pageStyle.getSpacerColor(this))
            chapter_title.setTextColor(pageStyle.getLabelColor(this))
            if (pageStyle.isDark || pageStyle == PageStyle.STYLE_0 && globalConfig.appDayNightMode == AppCompatDelegate.MODE_NIGHT_YES) {
                ImmersionBar.with(this).statusBarDarkFont(false).init()
            } else {
                ImmersionBar.with(this).statusBarDarkFont(true).init()
            }
            adapter.notifyDataSetChanged()
        })
        setting.setOnClickListener {
            launch("setting") {
                drawer_layout?.closeDrawer(GravityCompat.END)
                drawer_layout?.postOnAnimation {
                    BookReaderSettingFragment().show(
                        supportFragmentManager,
                        "BookReaderSettingFragment"
                    )
                }
            }
        }


        if (globalConfig.appDayNightMode == AppCompatDelegate.MODE_NIGHT_NO) {
            day_night.setImageResource(R.drawable.ic_theme_dark_24px)
        } else {
            day_night.setImageResource(R.drawable.ic_theme_light_24px)
        }
        day_night.setOnClickListener {
            when (globalConfig.appDayNightMode) {
                AppCompatDelegate.MODE_NIGHT_NO -> {
                    day_night.setImageResource(R.drawable.ic_theme_light_24px)
                    globalConfig.appDayNightMode = AppCompatDelegate.MODE_NIGHT_YES
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    //切换成深色模式。阅读器样式自动调整为上一次的深色样式
                    globalConfig.readerPageStyle.postValue(globalConfig.lastDarkTheme.value)
                }
                else -> {
                    day_night.setImageResource(R.drawable.ic_theme_dark_24px)
                    globalConfig.appDayNightMode = AppCompatDelegate.MODE_NIGHT_NO
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    //切换成浅色模式。阅读器样式自动调整为上一次的浅色样式
                    globalConfig.readerPageStyle.postValue(globalConfig.lastLightTheme.value)
                }
            }

        }
    }

    /**
     * TTS 语音阅读
     */
    private fun initTTS() {
        chapter_title.setOnClickListener {
            if (ttsUtil.isSpeaking) {
                ttsUtil.stop()
                return@setOnClickListener
            }
            val manager = recycle_view.layoutManager as LinearLayoutManager
            val position = manager.findLastVisibleItemPosition()
            val chapter = adapter.chapterList.getOrNull(position) ?: return@setOnClickListener

            ttsUtil.progressChangeCallback = { chapterIndex, progress, content ->

                launch(singleCoroutineKey = "progressChangeCallback") {
                    val view = manager.findViewByPosition(chapterIndex)
                    if (view != null) {
                        var dy =
                            -(view.height * progress.toFloat() / 100 - recycle_view.height / 2).roundToInt()
                        dy = max(min(dy, 0), -view.height)

                        manager.scrollToPositionWithOffset(chapterIndex, dy)
                        refreshChapterProgress()

                        if (ttsUtil.isSpeakEnd) {
                            adapter.chapterList.getOrNull(chapterIndex + 1)?.also {
                                if (getChapterContent(it, false)) {
                                    adapter.notifyDataSetChanged()
                                }
                                speak(it, 0)
                            }
                        }
                    }

                }

                launch {
                    if (preLoadRefresh(
                            adapter.chapterList,
                            chapterIndex - 1..chapterIndex + 3
                        )
                    ) {
                        adapter.notifyDataSetChanged()
                    }
                }

            }
            launch(singleCoroutineKey = "speak") {
                val view = manager.findViewByPosition(position)
                val y = view?.run {
                    val virtical = max(-(y + chapter_content.y), 0f)
                    val layout = chapter_content.layout
                    if (layout != null) {
                        val line = layout.getLineForVertical(virtical.roundToInt())
                        layout.getLineStart(line)
                    } else {
                        0
                    }

                } ?: 0

                speak(chapter, y)
            }
        }
    }

    private fun initScrollLoadChapter() {
        recycle_view.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var preLastPos = -1
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val manager = recyclerView.layoutManager as LinearLayoutManager
                val firstPos = manager.findFirstVisibleItemPosition()
                val lastPos = manager.findLastVisibleItemPosition()

                if (preLastPos != lastPos) {
                    preLastPos = lastPos

                    val chapterList = adapter.chapterList
                    val chapter = chapterList.getOrNull(lastPos) ?: return
                    chapter_title.text = chapter.title

                    launch(singleCoroutineKey = "onScrolledCheckChapterContentCache") {
                        val minIndex = max(firstPos - 1, 0)
                        val maxIndex = min(lastPos + 3, chapterList.size)

                        val update = preLoadRefresh(chapterList, minIndex..maxIndex)
                        if (update) {
                            val curFirstPos = manager.findFirstVisibleItemPosition()
                            val curLastPos = manager.findLastVisibleItemPosition()
                            if (curFirstPos <= maxIndex && curLastPos >= minIndex) {
                                delay(1)
                                adapter.notifyDataSetChanged()
                            }
                        }
                    }
                    saveReadRecord()
                }
                refreshChapterProgress()
            }
        })
    }

    private fun refreshChapterProgress() {
        launchIo(singleCoroutineKey = "refreshChapterProgress") {
            delay(300)
            withMain {
                val manager = recycle_view.layoutManager as LinearLayoutManager
                val view = manager.getChildAt(0) ?: return@withMain
                reader_progress.max = max(view.height - recycle_view.height, 1)
                reader_progress.progress = min(abs(view.top), reader_progress.max)
            }
        }
    }

    private fun saveReadRecord() {
        recycle_view ?: return
        val manager = recycle_view.layoutManager as LinearLayoutManager
        var view = manager.getChildAt(0) ?: return
        var isEnd = view.height + view.top - recycle_view.height < recycle_view.height / 6

        if (isEnd && manager.findLastVisibleItemPosition() == adapter.chapterList.size - 1) {
            view = manager.getChildAt(manager.childCount - 1) ?: view
            isEnd = view.height + view.top - recycle_view.height < recycle_view.height / 6
        }

        val top = view.top
        val pos = manager.getPosition(view)
        val readingChapter = adapter.chapterList.getOrNull(pos)
        readingRecord.chapterIndex = readingChapter?.index ?: readingRecord.chapterIndex
        readingRecord.offest = top

        readingRecord.isEnd = isEnd && readingChapter?.isLoaded == true
        launchIo {
            DataManager.setReadingRecord(readingRecord)
            WebDavMgr.sync { uploadReadingRecord() }
        }
    }

    private val initDataJob = AtomicReference<Job>()
    private fun initData() {
        initDataJob.get()?.cancel()
        launch(singleCoroutineKey = "initBookReaderData") {
            val book = DataManager.getBookById(bookId)
            if (book == null) {
                finish()
                return@launch
            }
            //设置章节列表
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

            readingRecord.bookId = bookId
            if (chapterIndex != -1) {
                readingRecord.chapterIndex = chapterIndex
                readingRecord.offest = 0
                readingRecord.isEnd = false
                DataManager.setReadingRecord(readingRecord)
            }

            var first = true
            DataManager.getChapterList(bookId).collectLatest {
                if (first) {
                    first = false

                    var index = it.indexOfFirst { chapter ->
                        chapter.index == readingRecord.chapterIndex
                    }
                    if (index > -1) {
                        if (readingRecord.isEnd && index < it.size - 1) {
                            readingRecord.offest = 0
                            index++
                        }
                        if (!it[index].isLoaded) {
                            readingRecord.offest = 0
                        }
                    }
                    //只加载本地的数据
                    val intRange = max(index - 1, 0)..min(index + 3, it.size - 1)
                    preLoadRefresh(it, intRange, true)
                    if (adapter.chapterList.size != it.size) {
                        adapter.chapterList = it
                        adapter.notifyDataSetChanged()
                    }


                    if (index != -1) {
                        val manager = recycle_view.layoutManager as LinearLayoutManager
                        manager.scrollToPositionWithOffset(index, readingRecord.offest)
                    }
                } else {
                    if (adapter.chapterList.size != it.size) {
                        adapter.chapterList = it
                        adapter.notifyDataSetChanged()
                    }
                }
            }

        }.also(initDataJob::set)
    }

    private suspend fun preLoadRefresh(
        chapterList: List<Chapter>,
        posRange: IntRange,
        onlyLocal: Boolean = false
    ): Boolean = withIo {
        val loadList = posRange.mapNotNull { chapterList.getOrNull(it) }
        loadList.map {
            async { getChapterContent(it, onlyLocal) }
        }.awaitAll().firstOrNull { it } ?: false
    }

    /**
     * 加载 上一章 当前章 下一章
     */
    private suspend fun getChapterContent(
        chapter: Chapter?,
        onlyLocal: Boolean
    ) = withIo {
        chapter ?: return@withIo false
        if (chapter.isLoaded && chapter.content != null) {
            return@withIo false
        }

        if (!chapter.isLoading.compareAndSet(false, true)) {
            return@withIo false
        }

        try {
            DataManager.getChapterContent(chapter, onlyLocal)
        } finally {
            chapter.isLoading.lazySet(false)
        }

        return@withIo true
    }

    class ContentAdapter(val activity: BookReaderActivity) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val loadingStr = "拼命加载中…………………………………………………………………………………………………………………………"

        init {
            setHasStableIds(true)
        }

        var chapterList = emptyList<Chapter>()
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return object : RecyclerView.ViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.reader_item_activity_chapter_content,
                    parent,
                    false
                )
            ) {}
        }

        override fun getItemCount(): Int = chapterList.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val chapter = chapterList[position]
            holder.itemView.apply {

                (tag as? Job)?.apply {
                    if (isActive) {
                        cancel()
                    }
                }
                val fontSize = globalConfig.readerFontSize.value!!
                chapter_title.setTextSize(TypedValue.COMPLEX_UNIT_SP, (fontSize + 4).toFloat())
                chapter_content.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())

                chapter_content.setLineSpacing(0f, globalConfig.readerLineSpacing.value!!)

                val pageStyle = PageStyle.getStyle(globalConfig.readerPageStyle.value!!)
                chapter_title.setTextColor(pageStyle.getChapterTitleColor(context))
                chapter_content.setTextColor(pageStyle.getChapterContentColor(context))

                activity.launchIo {
                    val background = pageStyle.getBackground(context)
                    withMain {
                        chapter_content_background.setImageDrawable(background)
                    }
                }

                chapter_title.text = chapter.title
                chapter_title.setOnClickListener {
                    activity.launch {
                        showSnackbar(it, "正在加载……")
                        DataManager.getChapterContent(chapter, false, force = true)
                        showSnackbar(it, "加载完成")
                        if (holder.adapterPosition == position) {
                            delay(1)
                            notifyItemChanged(position)
                        }
                    }
                }

                isClickable = false
                if (chapter.content != null) {
                    if (chapter.isLoaded) {
                        val cacheFormat = chapter.content?.cacheFormat()
                        if (cacheFormat != null) {
                            chapter_content.text = cacheFormat
                            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        } else {
                            chapter_content.text = "正在处理数据……"
                            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT

                            tag = activity.launchIo {
                                val format = chapter.content?.format()
                                withMain {
                                    chapter_content.text = format
                                    if (chapter.isLoaded) {
                                        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    }
                                }
                            }

                        }
                    } else {
                        chapter_content.text = chapter.content?.content ?: loadingStr
                        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                        setOnClickListener {
                            chapter_content.text = loadingStr
                            activity.launch {
                                showSnackbar(it, "正在加载……")
                                val intRange =
                                    max(position - 1, 0)..min(position + 3, chapterList.size - 1)
                                val update = activity.preLoadRefresh(chapterList, intRange)
                                showSnackbar(it, "加载完成")
                                if (update && holder.absoluteAdapterPosition == position) {
                                    delay(1)
                                    notifyDataSetChanged()
                                }
                            }
                        }
                    }
                } else {
                    layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    chapter_content.text = loadingStr
                }
            }

        }

        override fun getItemId(position: Int): Long {
            return chapterList[position].index.toLong()
        }
    }
}
