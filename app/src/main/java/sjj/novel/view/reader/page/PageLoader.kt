package sjj.novel.view.reader.page

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import androidx.lifecycle.ViewModel
import com.jaeger.library.OnSelectListener
import com.jaeger.library.SelectableTextHelper
import com.jaeger.library.SelectionInfo
import com.jaeger.library.TxtLocation
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.utils.dp2Px
import com.zqc.opencc.android.lib.ChineseConverter
import com.zqc.opencc.android.lib.ConversionType
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import sjj.alog.Log
import sjj.novel.view.reader.animation.BitmapWrapper
import sjj.novel.view.reader.bean.BookBean
import sjj.novel.view.reader.bean.BookRecordBean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Created by newbiechen on 17-7-1.
 */
abstract class PageLoader : ViewModel(), OnSelectListener {
    private val breakIteratorTl = ThreadLocal.withInitial {
        android.icu.text.BreakIterator.getCharacterInstance()
    }

    /**
     * 获取章节目录。
     *
     * @return
     */
    // 当前章节列表
    var chapterCategory: MutableList<TxtChapter>? = null
        protected set

    // 书本对象
    var mCollBook: BookBean? = null

    // 监听器
    @JvmField
    protected var mPageChangeListener: OnPageChangeListener? = null

    @SuppressLint("StaticFieldLeak")
    private var mPageView: PageView? = null

    // 当前显示的页
    private var mCurPage: TxtPage? = null
        set(value) {
            field = value
            saveRecord()
        }

    private var ttsSpeakLine: TxtLine? = null

    // 当前章节的页面列表
    var curPageList: List<TxtPage>? = null

    // 绘制提示的画笔
    private var mTipPaint: Paint? = null

    // 绘制标题的画笔
    private var mTitlePaint: TextPaint? = null

    // 高亮文字选中测试
    private var mSelectedColorTest = false
    private var mSelectedPaint: Paint? = null

    // 绘制小说内容的画笔
    private var mTextPaint: TextPaint? = null

    // 被遮盖的页，或者认为被取消显示的页
    private var mCancelPage: TxtPage? = null

    // 存储阅读记录类
    var mBookRecord = BookRecordBean()
        private set
    private var mPreLoadDisp: Job? = null

    // 简繁转换模式: 0=关闭, 1=简体转繁体, 2=繁体转简体
    private var mJianFanMode: Int = MODE_JIAN_FAN_OFF

    // 排版模式: 0=横排左起, 1=横排右起, 2=竖排左起, 3=竖排右起
    private var mTypesettingMode: Int = MODE_TYPESETTING_HORIZONTAL_LTR

    private val isVerticalTypesetting: Boolean
        get() = mTypesettingMode == MODE_TYPESETTING_VERTICAL_LTR || mTypesettingMode == MODE_TYPESETTING_VERTICAL_RTL

    /*****************params */ // 当前的状态

    protected var mStatus: Int = STATUS_LOADING

    // 判断章节列表是否加载完成
    @JvmField
    protected var isChapterListPrepare: Boolean = false

    // 是否打开过章节
    var isChapterOpen: Boolean = false
        private set
    private var isFirstOpen = true

    // 页面的翻页效果模式
    private var mPageMode: PageMode = PageMode.SIMULATION

    val mDisplayParams: DisplayParams = DisplayParams()

    //当前页面的背景
    private var mBackground: BgDrawable? = null

    /**
     * 获取当前章节的章节位置
     *
     * @return
     */
    // 当前章
    var chapterPos: Int = 0
        set(value) {
            field = value
            Log.i("chapterPos:$field")
        }


    //上一章的记录
    private var mLastChapterPos = 0
    private var mSelectableTextHelper: SelectableTextHelper? = null
    private val mLocation = TxtLocationImpl()

    init {
        chapterCategory = mutableListOf()
        // 初始化画笔
        initPaint()
    }

    fun initPageView(pageView: PageView) {
        mPageView = pageView
        mSelectableTextHelper = SelectableTextHelper.Builder(pageView, mLocation).build()
        mSelectableTextHelper?.setSelectListener(this)
        // 初始化PageView
        pageView.setPageMode(mPageMode)
        pageView.setBackground(mBackground)
    }

    fun setTypeface(typeface: Typeface?) {
        mTipPaint!!.setTypeface(typeface)
        mTextPaint!!.setTypeface(typeface)
        mTitlePaint!!.setTypeface(typeface)
        mPageView!!.drawCurPage()
    }

    private fun initPaint() {
        // 绘制提示的画笔
        mTipPaint = Paint()
        mTipPaint!!.setTextAlign(Paint.Align.LEFT) // 绘制的起始点
        mTipPaint!!.setTextSize(12.dp2Px.toFloat()) // Tip默认的字体大小
        mTipPaint!!.setAntiAlias(true)
        mTipPaint!!.setSubpixelText(true)

        // 绘制页面内容的画笔
        mTextPaint = TextPaint()
        mTextPaint!!.setSubpixelText(true)
        mTextPaint!!.setAntiAlias(true)

        // 绘制标题的画笔
        mTitlePaint = TextPaint()
        mTitlePaint!!.setSubpixelText(true)
        mTitlePaint!!.setStyle(Paint.Style.FILL_AND_STROKE)
        mTitlePaint!!.setTypeface(Typeface.DEFAULT_BOLD)
        mTitlePaint!!.setAntiAlias(true)

        // 绘制背景的画笔
        mSelectedPaint = Paint()
        mSelectedPaint!!.setSubpixelText(true)
        mSelectedPaint!!.setAntiAlias(true)
        mSelectedPaint!!.setStyle(Paint.Style.FILL_AND_STROKE)
    }

    /****************************** public method */
    /**
     * 跳转到上一章
     *
     * @return
     */
    fun skipPreChapter(): Boolean {
        Log.i("skipPreChapter")
        if (!hasPrevChapter()) {
            return false
        }

        // 载入上一章。
        if (parsePrevChapter()) {
            mCurPage = getCurPage(0)
        } else {
            mCurPage = TxtPage()
        }
        mPageView!!.drawCurPage()
        return true
    }

    /**
     * 跳转到下一章
     *
     * @return
     */
    fun skipNextChapter(): Boolean {
        Log.i("skipNextChapter")
        if (!hasNextChapter()) {
            return false
        }

        //判断是否达到章节的终止点
        if (parseNextChapter()) {
            mCurPage = getCurPage(0)
        } else {
            mCurPage = TxtPage()
        }
        mPageView!!.drawCurPage()
        return true
    }

    /**
     * 跳转到指定章节
     *
     * @param pos:从 0 开始。
     */
    fun skipToChapter(pos: Int) {
        Log.i("skipToChapter pos:$pos")
        // 设置参数
        this.chapterPos = pos
        // 如果当前下一章缓存正在执行，则取消
        mPreLoadDisp?.cancel()
        // 打开指定章节
        openChapter()
    }

    fun setTtsSpeakLine(lines: TxtLine?) {
        ttsSpeakLine = lines
        mPageView?.drawCurPage()
    }

    /**
     * 跳转到指定的页
     *
     * @param pos
     */
    fun skipToPage(pos: Int): Boolean {
        Log.i("skipToPage pos:" + pos)
        if (!isChapterListPrepare) {
            return false
        }
        mCurPage = getCurPage(pos)
        mPageView!!.drawCurPage()
        return true
    }

    /**
     * 设置文字相关参数
     *
     * @param textSize
     */
    fun setTextSize(textSize: Float, lineSpace: Float, paraSpace: Float, letterSpacing: Float) {
        // 文字大小
        mDisplayParams.lineInterval = lineSpace
        mDisplayParams.paraInterval = paraSpace
        mDisplayParams.letterSpacing = letterSpacing
        mTextPaint!!.textSize = textSize

        mDisplayParams.titleParaInterval = paraSpace * 1.5f
        mTitlePaint!!.textSize = textSize * 1.1f
        // 取消缓存
        ChapterPageCache.resetTextSize(textSize, lineSpace, paraSpace, letterSpacing)

        // 如果当前已经显示数据
        if (isChapterListPrepare && mStatus == STATUS_FINISH) {
            // 重新计算当前页面
            dealLoadPageList(this.chapterPos)
            // 重新获取指定页面
            mCurPage = curPageList?.getOrNull(mCurPage?.position ?: -1) ?: curPageList?.lastOrNull()
        }

        mPageView!!.drawCurPage()
    }

    /**
     * 设置页面样式
     *
     * @param pageStyle:页面样式
     */
    fun setPageStyle(pageStyle: PageStyle, selectedColorTest: Boolean = false) {
        Log.i("设置页面样式:$pageStyle")
        // 设置当前颜色样式
        mBackground = BgDrawable(pageStyle.getBackground(mPageView!!.context, 0, 0))

        mTipPaint!!.setColor(pageStyle.getLabelColor(mPageView!!.context))
        mTitlePaint!!.setColor(pageStyle.getChapterTitleColor(mPageView!!.context))
        mTextPaint!!.setColor(pageStyle.getChapterContentColor(mPageView!!.context))
        mSelectedPaint!!.setColor(pageStyle.getSelectedColor(mPageView!!.context))
        mSelectedColorTest = selectedColorTest
        mPageView!!.drawCurPage()
    }

    /**
     * 翻页动画
     *
     * @param pageMode:翻页模式
     * @see PageMode
     */
    fun setPageMode(pageMode: PageMode) {
        mPageMode = pageMode

        mPageView!!.setPageMode(pageMode)

        // 重新绘制当前页
        mPageView!!.drawCurPage()
    }

    /**
     * 设置页面切换监听
     *
     * @param listener
     */
    fun setOnPageChangeListener(listener: OnPageChangeListener?) {
        mPageChangeListener = listener
    }

    var pageStatus: Int
        get() = mStatus
        set(status) {
            mStatus = status
            mPageView!!.drawCurPage()
        }

    val pagePos: Int
        /**
         * 获取当前页的页码
         *
         * @return
         */
        get() = if (mCurPage == null) 0 else mCurPage!!.position

    val curChapter: TxtChapter?
        get() {
            Log.i("getCurChapter mCurChapterPos:" + this.chapterPos)
            return chapterCategory?.getOrNull(chapterPos)
        }

    /**
     * 保存阅读记录
     */
    fun saveRecord() {
        val chapterList = this.chapterCategory ?: return
        val collBook = this.mCollBook
        val curPage = this.mCurPage
        val bookRecord = this.mBookRecord
        val curChapterPos = this.chapterPos
        val curPageList = this.curPageList
        if (!isChapterOpen || chapterList.isEmpty() || collBook == null || curPage == null || curPageList == null || mStatus != STATUS_FINISH) {
            return
        }
        bookRecord.bookId = collBook.id
        val record = mPageView?.getBookRecord()
        if (record != null) {
            bookRecord.chapter = record.chapter
            bookRecord.pagePos = record.pagePos
            bookRecord.scrollOffset = record.scrollOffset
        } else {
            bookRecord.chapter = curChapterPos
            bookRecord.pagePos = curPage.position
            bookRecord.scrollOffset = 0
        }

        bookRecord.isEnd =
            curChapterPos == chapterList.size - 1 && curPageList.size == curPage.position + 1

        Log.i("保存阅读记录:${bookRecord}")
        mPageChangeListener!!.onBookRecordChange(bookRecord)
    }

    fun setBookRecord(record: BookRecordBean) {
        Log.i("恢复阅读记录:${record}")
        mBookRecord = record
        mPageView?.setBookRecord(record)
        this.chapterPos = record.chapter
        mLastChapterPos = this.chapterPos
        if (isChapterOpen) {
            skipToChapter(record.chapter)
            skipToPage(record.pagePos)
        }
    }

    /**
     * 打开指定章节
     */
    fun openChapter() {
        isFirstOpen = false

        if (!mPageView!!.isPrepare) {
            Log.e("阅读页没准备好")
            return
        }

        // 如果章节目录没有准备好
        if (!isChapterListPrepare) {
            Log.e("章节数没准备好")
            mStatus = STATUS_LOADING
            mPageView!!.drawCurPage()
            return
        }

        // 如果获取到的章节目录为空
        if (chapterCategory!!.isEmpty()) {
            Log.e("章节为空")
            mStatus = STATUS_CATEGORY_EMPTY
            mPageView!!.drawCurPage()
            return
        }

        if (parseCurChapter()) {
            Log.e("章节解析完成 page size:${curPageList?.size} title:${curPageList?.firstOrNull()?.title}")
            // 如果章节从未打开
            if (!isChapterOpen) {
                var position = mBookRecord.pagePos

                // 防止记录页的页号，大于当前最大页号
                if (position >= curPageList!!.size) {
                    position = curPageList!!.size - 1
                }
                // 切换状态
                isChapterOpen = true
                mCurPage = getCurPage(position)
                mCancelPage = mCurPage
            } else {
                if (mLastChapterPos == this.chapterPos + 1) {
                    mCurPage = getCurPage(curPageList!!.size - 1)
                } else {
                    mCurPage = getCurPage(0)
                }

            }
        } else {
            mCurPage = TxtPage()
        }

        mPageView!!.drawCurPage()
    }

    fun chapterError() {
        //加载错误
        mStatus = STATUS_ERROR
        mPageView!!.drawCurPage()
    }

    /**
     * 关闭书本
     */
    fun closeBook() {
        Log.i("chapterCategory size:${chapterCategory?.size} isChapterOpen:$isChapterOpen isChapterListPrepare:$isChapterListPrepare")
        isChapterOpen = false
        isChapterListPrepare = false
        mPreLoadDisp?.cancel()
        chapterCategory?.clear()
        this.chapterCategory = null
        this.curPageList = null
        mCurPage = null
    }

    /**
     * 加载页面列表
     *
     * @param chapterPos:章节序号
     * @return
     */
    @Throws(Exception::class)
    private fun loadPageList(chapterPos: Int): List<TxtPage>? {
        // 获取章节
        val chapter = chapterCategory?.getOrNull(chapterPos)
        if (chapter == null) {
            Log.e("章节不存在 chapterPos:$chapterPos chapterCategory size:${chapterCategory?.size}")
            return null
        }
        // 判断章节是否存在
        if (!hasChapterData(chapter)) {
            Log.e("章节数据不存在 chapterPos:" + chapterPos + " title:" + chapter.title)
            return null
        }
        val txtPages = ChapterPageCache.get(chapterPos)
        if (!txtPages.isNullOrEmpty()) {
            return txtPages
        }
        // 获取章节的文本流
        val pages = loadPages(chapter)
        if (pages.isNotEmpty()) {
            ChapterPageCache.put(chapterPos, pages)
        }
        return pages
    }

    /*******************************abstract method */
    /**
     * 刷新章节列表
     */
    abstract fun refreshChapterList()

    /**
     * 章节数据是否存在
     *
     * @return
     */
    protected fun hasChapterData(chapter: TxtChapter?): Boolean {
        return chapter?.content != null
    }

    /***********************************default method */
    fun drawPage(bitmap: BitmapWrapper) {
        drawBackground(mPageView?.bgBitmap?.bitmap ?: return)
        bitmap.chapterPos = this.chapterPos
        bitmap.pagePos = pagePos
        if (!bitmap.inited) {
            bitmap.inited = mStatus == STATUS_FINISH && mCurPage != null
        }
        drawContent(bitmap.bitmap)
        //更新绘制
        mPageView!!.invalidate()
    }

    private fun drawBackground(bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        val tipMarginHeight = 3.dp2Px
        /****绘制背景 */
        mBackground?.draw(canvas)
        val chapters = chapterCategory
        val tipPaint = mTipPaint!!
        if (chapters?.isEmpty() == false) {
            /*****初始化标题的参数 */
            //需要注意的是:绘制text的y的起始点是text的基准线的位置，而不是从text的头部的位置
            val tipTop = mDisplayParams.insetTop + mDisplayParams.tipHeight / 2 + (tipPaint.fontMetrics.bottom - tipPaint.fontMetrics.top) / 2
            //根据状态不一样，数据不一样
            if (mStatus != STATUS_FINISH) {
                if (isChapterListPrepare) {
                    canvas.drawText(chapters[this.chapterPos].title, mDisplayParams.contentLeft, tipTop, tipPaint)
                }
            } else {
                val curPage = mCurPage ?: return
                val pageList = curPageList ?: return

                /******绘制页码 */
                val percent = (curPage.position + 1).toString() + "/" + pageList.size
                canvas.drawText(percent, mDisplayParams.contentRight - tipPaint.measureText(percent), tipTop, tipPaint)

                val count = tipPaint.breakText(curPage.title, true, mDisplayParams.contentRight - tipPaint.measureText(percent) - tipMarginHeight, null)
                canvas.drawText(curPage.title, 0, count, mDisplayParams.contentLeft, tipTop, tipPaint)
            }
        }
    }

    private fun drawContent(bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        if (BuildConfig.DEBUG) {
            canvas.drawLine(mDisplayParams.contentLeft, 0f, mDisplayParams.contentLeft, mDisplayParams.height.toFloat(), mTitlePaint!!)
            canvas.drawLine(mDisplayParams.contentRight, 0f, mDisplayParams.contentRight, mDisplayParams.height.toFloat(), mTitlePaint!!)

            canvas.drawLine(0f, mDisplayParams.contentTop, mDisplayParams.width.toFloat(), mDisplayParams.contentTop, mTitlePaint!!)
            canvas.drawLine(0f, mDisplayParams.contentBottom, mDisplayParams.width.toFloat(), mDisplayParams.contentBottom, mTitlePaint!!)
        }
        /******绘制内容 */
        val curPage = mCurPage
        if (mStatus != STATUS_FINISH || curPage == null) {
            //绘制字体
            var tip = ""
            when (mStatus) {
                STATUS_LOADING -> tip = "正在拼命加载中..."
                STATUS_ERROR -> tip = "加载失败(点击边缘重试)"
                STATUS_EMPTY -> tip = "文章内容为空"
                STATUS_PARING -> tip = "正在排版请等待..."
                STATUS_PARSE_ERROR -> tip = "文件解析错误"
                STATUS_CATEGORY_EMPTY -> tip = "目录列表为空"
            }

            //将提示语句放到正中间
            val fontMetrics = mTextPaint!!.getFontMetrics()
            val textHeight = fontMetrics.top - fontMetrics.bottom
            val textWidth = mTextPaint!!.measureText(tip)
            val pivotX = (mDisplayParams.contentWidth - textWidth) / 2
            val pivotY = (mDisplayParams.contentHeight - textHeight) / 2
            canvas.drawText(tip, pivotX, pivotY, mTextPaint!!)
        } else {
            val helper = mSelectableTextHelper
            if (!isVerticalTypesetting && helper?.mSelectionInfo?.select == true) {
                val start = helper.mSelectionInfo.start
                val end = helper.mSelectionInfo.end

                val startLine = mLocation.getLineForOffset(start)
                val endLine = mLocation.getLineForOffset(end)
                //                Log.e("startLine:" + startLine + " endLine:" + endLine + " start:" + start + " end:" + end);
                for (i in startLine..endLine) {
                    val txtLine = curPage.lines.get(i)
                    val left: Float
                    val right: Float
                    if (startLine == i) {
                        left = mLocation.getHorizontalLeft(start)
                    } else {
                        left = mLocation.getLineStart(i)
                    }
                    if (endLine == i) {
                        right = mLocation.getHorizontalRight(end)
                    } else {
                        right = mLocation.getLineEnd(i)
                    }
                    //                    Log.e("left:" + left + " top:" + txtLine.top + " right:" + right + " bottom:" + txtLine.bottom + " " + txtLine);
                    canvas.drawRect(left, txtLine.top, right, txtLine.bottom, mSelectedPaint!!)
                }
            }

            if (mSelectedColorTest) {
                val line = curPage.lines.firstOrNull()
                if (line != null) {
                    canvas.drawRect(line.left, line.top, line.right, line.bottom, mSelectedPaint!!)
                }
            }
            ttsSpeakLine?.let { line ->
                if (line in curPage.lines) {
                    var startIndex = 0
                    var endIndex = line.clusterBoundaries.size - 2
                    for (i in 0 until line.clusterBoundaries.size - 1) {
                        val isBlank = line.txt.substring(line.clusterBoundaries[i], line.clusterBoundaries[i + 1]).isBlank()
                        if (isBlank) {
                            continue
                        }
                        startIndex = i
                        break
                    }
                    for (i in line.clusterBoundaries.size - 2 downTo 0) {
                        val isBlank = line.txt.substring(line.clusterBoundaries[i], line.clusterBoundaries[i + 1]).isBlank()
                        if (isBlank) {
                            continue
                        }
                        endIndex = i
                        break
                    }
                    if (isVerticalTypesetting) {
                        val top = line.clusterLeft[startIndex]
                        val bottom = line.clusterRight[endIndex]
                        canvas.drawRect(line.left, top, line.right, bottom, mSelectedPaint!!)
                    } else {
                        if (mTypesettingMode == MODE_TYPESETTING_HORIZONTAL_RTL) {
                            val left = line.clusterLeft[endIndex]
                            val right = line.clusterRight[startIndex]
                            canvas.drawRect(left, line.top, right, line.bottom, mSelectedPaint!!)
                        } else {
                            val left = line.clusterLeft[startIndex]
                            val right = line.clusterRight[endIndex]
                            canvas.drawRect(left, line.top, right, line.bottom, mSelectedPaint!!)
                        }
                    }
                }
            }

            val titleMetrics = mTitlePaint!!.getFontMetrics()
            val titleBase = -titleMetrics.ascent
            val textMetrics = mTextPaint!!.getFontMetrics()
            val textBase = -textMetrics.ascent
            for (line in curPage.lines) {
                val paint: Paint = (if (line.isTitle) mTitlePaint else mTextPaint)!!
                val bounds = line.clusterBoundaries
                if (bounds.size >= 2) {
                    // 按 grapheme cluster 绘制（兼容 emoji / surrogate pair）
                    for (ci in 0 until bounds.size - 1) {
                        val s = bounds[ci]
                        val e = bounds[ci + 1]
                        if (isVerticalTypesetting) {
                            val x = line.left + (line.width - paint.measureText(line.txt, s, e)) / 2
                            val y = line.clusterLeft[ci] + (if (line.isTitle) titleBase else textBase)
                            canvas.drawText(line.txt, s, e, x, y, paint)

                            if (BuildConfig.DEBUG) {
                                canvas.drawLine(line.left, 0f, line.left, mDisplayParams.height.toFloat(), mTitlePaint!!)
                                canvas.drawLine(line.right, 0f, line.right, mDisplayParams.height.toFloat(), mTitlePaint!!)
                            }
                        } else {
                            val y = line.top + (if (line.isTitle) titleBase else textBase)
                            canvas.drawText(line.txt, s, e, line.clusterLeft[ci], y, paint)

                            if (BuildConfig.DEBUG) {
                                canvas.drawLine(0f, line.top, mDisplayParams.width.toFloat(), line.top, mTitlePaint!!)
                                canvas.drawLine(0f, line.bottom, mDisplayParams.width.toFloat(), line.bottom, mTitlePaint!!)
                            }
                        }
                    }
                } else {
                    // fallback: 整行一次性绘制
                    val y = line.top + (if (line.isTitle) titleBase else textBase)
                    canvas.drawText(line.txt, 0, line.txt.length, line.left, y, paint)
                }
            }
        }
    }

    fun prepareDisplay(w: Int, h: Int) {
        // 获取PageView的宽高
        mDisplayParams.width = w
        mDisplayParams.height = h
        ChapterPageCache.resetDisplay(w, h)
        // 获取内容显示位置的大小
        // 重置 PageMode
        mPageView!!.setPageMode(mPageMode)

        if (!isChapterOpen) {
            // 展示加载界面
            mPageView!!.drawCurPage()
            // 如果在 display 之前调用过 openChapter 肯定是无法打开的。
            // 所以需要通过 display 再重新调用一次。
            if (!isFirstOpen) {
                // 打开书籍
                openChapter()
            }
        } else {
            // 如果章节已显示，那么就重新计算页面
            if (mStatus == STATUS_FINISH) {
                dealLoadPageList(this.chapterPos)
                // 重新设置文章指针的位置
                mCurPage = getCurPage(mCurPage!!.position)
            }
            mPageView!!.drawCurPage()
        }
    }

    /**
     * 翻阅上一页
     *
     * @return
     */
    fun prev(): Boolean {
        // 以下情况禁止翻页
        if (!canTurnPage()) {
            return false
        }

        if (mStatus == STATUS_FINISH) {
            // 先查看是否存在上一页
            val prevPage = this.prevPage
            if (prevPage != null) {
                mCancelPage = mCurPage
                mCurPage = prevPage
                mPageView!!.drawNextPage()
                return true
            }
        }

        if (!hasPrevChapter()) {
            return false
        }

        mCancelPage = mCurPage
        if (parsePrevChapter()) {
            mCurPage = this.prevLastPage
        } else {
            mCurPage = TxtPage()
        }
        mPageView!!.drawNextPage()
        return true
    }

    /**
     * 解析上一章数据
     *
     * @return:数据是否解析成功
     */
    open fun parsePrevChapter(): Boolean {
        // 加载上一章数据
        val prevChapter = this.chapterPos - 1

        mLastChapterPos = this.chapterPos
        this.chapterPos = prevChapter

        // 判断是否具有上一章缓存
        val pageList = ChapterPageCache.get(this.chapterPos)
        if (pageList != null) {
            this.curPageList = pageList
        } else {
            dealLoadPageList(prevChapter)
        }
        return if (this.curPageList != null) true else false
    }

    private fun hasPrevChapter(): Boolean {
        //判断是否上一章节为空
        if (this.chapterPos - 1 < 0) {
            return false
        }
        return true
    }

    /**
     * 翻到下一页
     *
     * @return:是否允许翻页
     */
    fun next(): Boolean {
        // 以下情况禁止翻页
        if (!canTurnPage()) {
            return false
        }

        if (mStatus == STATUS_FINISH) {
            // 先查看是否存在下一页
            val nextPage = this.nextPage
            if (nextPage != null) {
                mCancelPage = mCurPage
                mCurPage = nextPage
                mPageView!!.drawNextPage()
                return true
            }
        }

        if (!hasNextChapter()) {
            return false
        }

        mCancelPage = mCurPage
        // 解析下一章数据
        if (parseNextChapter()) {
            mCurPage = curPageList!!.get(0)
        } else {
            mCurPage = TxtPage()
        }
        mPageView!!.drawNextPage()
        return true
    }

    private fun hasNextChapter(): Boolean {
        // 判断是否到达目录最后一章
        val category = chapterCategory ?: return false
        return this.chapterPos + 1 < category.size
    }

    fun reloadPages() {
        val chapter = chapterCategory?.getOrNull(chapterPos)
        TxtChapter.evict(chapter?.link)
        ChapterPageCache.remove(this.chapterPos)
        parseCurChapter()
        // 重新设置文章指针的位置
        mCurPage = getCurPage(mCurPage?.position ?: 0)
        mPageView!!.drawCurPage()
    }

    open fun parseCurChapter(): Boolean {
        // 解析数据
        Log.e("章节分页")
        dealLoadPageList(this.chapterPos)
        // 预加载下一页面
        Log.e("预加载")
        preLoadNextChapter()
        return if (this.curPageList != null) true else false
    }

    /**
     * 解析下一章数据
     *
     * @return:返回解析成功还是失败
     */
    open fun parseNextChapter(): Boolean {
        val nextChapter = this.chapterPos + 1
        mLastChapterPos = this.chapterPos
        this.chapterPos = nextChapter

        // 是否下一章数据已经预加载了
        val txtPages = ChapterPageCache.get(this.chapterPos)
        if (txtPages != null) {
            this.curPageList = txtPages
        } else {
            dealLoadPageList(nextChapter)
        }
        // 预加载下一页面
        preLoadNextChapter()
        return if (this.curPageList != null) true else false
    }

    private fun dealLoadPageList(chapterPos: Int) {
        try {
            this.curPageList = loadPageList(chapterPos)
            if (this.curPageList != null) {
                if (curPageList!!.isEmpty()) {
                    mStatus = STATUS_EMPTY
                    curPageList = listOf(TxtPage())
                } else {
                    mStatus = STATUS_FINISH
                }
            } else {
                Log.e("章节数据加载失败 chapterPos:$chapterPos")
                mStatus = STATUS_LOADING
            }
        } catch (e: Exception) {
            Log.e("分页加载失败", e)
            this.curPageList = null
            mStatus = STATUS_ERROR
        }

    }

    // 预加载下一章
    @OptIn(DelicateCoroutinesApi::class)
    fun preLoadNextChapter() {
        val nextChapter = this.chapterPos + 1

        // 如果不存在下一章，且下一章没有数据，则不进行加载。
        if (!hasNextChapter() || !hasChapterData(chapterCategory?.getOrNull(nextChapter))) {
            return
        }

        //如果之前正在加载则取消
        mPreLoadDisp?.cancel()

        //调用异步进行预加载加载
        mPreLoadDisp = GlobalScope.launch(Dispatchers.IO) {
            loadPageList(nextChapter)
        }
    }

    // 取消翻页
    fun pageCancel() {
        if (mCurPage!!.position == 0 && this.chapterPos > mLastChapterPos) { // 加载到下一章取消了
            val pageList = ChapterPageCache.get(mLastChapterPos)
            if (pageList != null) {
                cancelNextChapter()
            } else {
                if (parsePrevChapter()) {
                    mCurPage = this.prevLastPage
                } else {
                    mCurPage = TxtPage()
                }
            }
        } else if (this.curPageList == null || (mCurPage!!.position == curPageList!!.size - 1 && this.chapterPos < mLastChapterPos)) {  // 加载上一章取消了
            val pageList = ChapterPageCache.get(mLastChapterPos)
            if (pageList != null) {
                cancelPreChapter()
            } else {
                if (parseNextChapter()) {
                    mCurPage = curPageList!![0]
                } else {
                    mCurPage = TxtPage()
                }
            }
        } else {
            // 假设加载到下一页，又取消了。那么需要重新装载。
            mCurPage = mCancelPage
        }
    }

    private fun cancelNextChapter() {
        val temp = mLastChapterPos
        mLastChapterPos = this.chapterPos
        this.chapterPos = temp
        this.curPageList = ChapterPageCache.get(this.chapterPos)
        mCurPage = this.prevLastPage
        mCancelPage = null
    }

    private fun cancelPreChapter() {
        // 重置位置点
        val temp = mLastChapterPos
        mLastChapterPos = this.chapterPos
        this.chapterPos = temp
        // 重置页面列表
        this.curPageList = ChapterPageCache.get(this.chapterPos)

        mCurPage = getCurPage(0)
        mCancelPage = null
    }

    /**************************************private method */
    /**
     * 将章节数据，解析成页面列表
     *
     * @param chapter ：章节信息
     * @return
     */
    private fun loadPages(chapter: TxtChapter): MutableList<TxtPage> {
        Log.i("加载章节内容：" + chapter.title + " pos:" + this.chapterPos)
        val lines: MutableList<TxtLine> = ArrayList()
        val titleText = convertByJianFanMode(chapter.title.toString())
        val contentText = convertByJianFanMode(chapter.content.toString())
        return when (mTypesettingMode) {

            MODE_TYPESETTING_HORIZONTAL_RTL -> {
                createTxtLineHRTL(lines, titleText, mTitlePaint!!)
                createTxtLineHRTL(lines, contentText, mTextPaint!!)
                createPagesHTTB(lines, chapter)
            }

            MODE_TYPESETTING_VERTICAL_LTR -> {
                createTxtLineVTTB(lines, titleText, mTitlePaint!!)
                createTxtLineVTTB(lines, contentText, mTextPaint!!)
                createPagesVLTR(lines, chapter)
            }

            MODE_TYPESETTING_VERTICAL_RTL -> {
                createTxtLineVTTB(lines, titleText, mTitlePaint!!)
                createTxtLineVTTB(lines, contentText, mTextPaint!!)
                createPagesVRTL(lines, chapter)
            }

            else -> {
                createTxtLineHLTR(lines, titleText, mTitlePaint!!)
                createTxtLineHLTR(lines, contentText, mTextPaint!!)
                createPagesHTTB(lines, chapter)
            }
        }
    }

    /**
     * 将文本行分页，生成横排页面列表。文本行从上到下排列
     */
    private fun createPagesHTTB(lines: MutableList<TxtLine>, chapter: TxtChapter): MutableList<TxtPage> {
        val pages: MutableList<TxtPage> = ArrayList()
        val pageLine: MutableList<TxtLine> = ArrayList()
        var i = 0
        while (i < lines.size) {
            var top = mDisplayParams.contentTop
            var clusterStart = 0
            while (i < lines.size) {
                val line = lines[i]
                var lineInterval = mDisplayParams.lineInterval
                if (i == 0) {
                    lineInterval = mDisplayParams.titleParaInterval
                } else if (lines[i - 1].isParaEnd) {
                    lineInterval = if (lines[i - 1].isTitle) mDisplayParams.titleParaInterval else mDisplayParams.paraInterval
                }

                val remHeight = mDisplayParams.contentBottom - top - line.height - lineInterval
                if (remHeight < 0) {
                    break
                }
                top += lineInterval
                line.left = mDisplayParams.contentLeft
                line.top = top
                line.right = mDisplayParams.contentRight
                line.bottom = top + line.height

                line.index = pageLine.size
                line.clusterStart = clusterStart
                clusterStart += line.clusterBoundaries.size - 1
                pageLine.add(line)
                //                Log.e(line.txt + "-" + line.txt.endsWith("\n") + "-" + line.isTitle);
                i++
                top += line.height
            }
            //上下对齐，将剩余高度平均分配到行间距
            val remHeight = mDisplayParams.contentBottom - top
            if (remHeight > 0 && pageLine.size > 1 && remHeight < pageLine.last().height + mDisplayParams.paraInterval) {
                val extra = remHeight / (pageLine.size - 1)
                for (j in 1 until pageLine.size) {
                    pageLine[j].top += extra * j
                    pageLine[j].bottom += extra * j
                }
            }
            val page = TxtPage()
            page.position = pages.size
            page.title = chapter.title
            page.lines = pageLine.toList()
            pages.add(page)
            pageLine.clear()
        }
        return pages
    }

    /**
     * 将文本行分页，生成竖排页面列表。文本行从左到右
     */
    private fun createPagesVLTR(lines: MutableList<TxtLine>, chapter: TxtChapter): MutableList<TxtPage> {
        val pages: MutableList<TxtPage> = ArrayList()
        val pageLine: MutableList<TxtLine> = ArrayList()
        var i = 0
        while (i < lines.size) {
            var left = mDisplayParams.contentLeft
            var clusterStart = 0
            while (i < lines.size) {
                val line = lines[i]
                var lineInterval = 0f
                if (pageLine.lastOrNull()?.isParaEnd == true) {
                    lineInterval = if (lines[i - 1].isTitle) mDisplayParams.titleParaInterval else mDisplayParams.paraInterval
                } else if (pageLine.lastOrNull()?.isParaEnd == false) {
                    lineInterval = mDisplayParams.lineInterval
                }

                val remWidth = mDisplayParams.contentRight - left - line.width - lineInterval
                if (remWidth < 0) {
                    break
                }
                left += lineInterval
                line.left = left
                line.top = mDisplayParams.contentTop
                line.right = left + line.width
                line.bottom = mDisplayParams.contentBottom

                line.index = pageLine.size
                line.clusterStart = clusterStart
                clusterStart += line.clusterBoundaries.size - 1
                pageLine.add(line)
                //                Log.e(line.txt + "-" + line.txt.endsWith("\n") + "-" + line.isTitle);
                i++
                left += line.width
            }
            val remWidth = mDisplayParams.contentRight - left
            if (remWidth > 0 && pageLine.size > 1 && remWidth < pageLine.last().width + mDisplayParams.paraInterval) {
                val extra = remWidth / (pageLine.size - 1)
                for (j in 1 until pageLine.size) {
                    pageLine[j].left += extra * j
                    pageLine[j].right += extra * j
                }
            }
            val page = TxtPage()
            page.position = pages.size
            page.title = chapter.title
            page.lines = pageLine.toList()
            pages.add(page)
            pageLine.clear()
        }
        return pages
    }

    /**
     * 将文本行分页，生成竖排页面列表。文本行从右到左
     */
    private fun createPagesVRTL(lines: MutableList<TxtLine>, chapter: TxtChapter): MutableList<TxtPage> {
        val pages: MutableList<TxtPage> = ArrayList()
        val pageLine: MutableList<TxtLine> = ArrayList()
        var i = 0
        while (i < lines.size) {
            var right = mDisplayParams.contentRight
            var clusterStart = 0
            while (i < lines.size) {
                val line = lines[i]
                var lineInterval = 0f
                if (pageLine.lastOrNull()?.isParaEnd == true) {
                    lineInterval = if (lines[i - 1].isTitle) mDisplayParams.titleParaInterval else mDisplayParams.paraInterval
                } else if (pageLine.lastOrNull()?.isParaEnd == false) {
                    lineInterval = mDisplayParams.lineInterval
                }

                val remWidth = right - mDisplayParams.contentLeft - line.width - lineInterval
                if (remWidth < 0) {
                    break
                }
                right -= lineInterval
                line.left = right - line.width
                line.top = mDisplayParams.contentTop
                line.right = right
                line.bottom = mDisplayParams.contentBottom

                line.index = pageLine.size
                line.clusterStart = clusterStart
                clusterStart += line.clusterBoundaries.size - 1
                pageLine.add(line)
                //                Log.e(line.txt + "-" + line.txt.endsWith("\n") + "-" + line.isTitle);
                i++
                right -= line.width
            }
            val remWidth = right - mDisplayParams.contentLeft
            if (remWidth > 0 && pageLine.size > 1 && remWidth < pageLine.last().width + mDisplayParams.paraInterval) {
                val extra = remWidth / (pageLine.size - 1)
                for (j in 1 until pageLine.size) {
                    pageLine[j].left -= extra * j
                    pageLine[j].right -= extra * j
                }
            }
            val page = TxtPage()
            page.position = pages.size
            page.title = chapter.title
            page.lines = pageLine.toList()
            pages.add(page)
            pageLine.clear()
        }
        return pages
    }

    private fun convertByJianFanMode(text: String): String {
        var text = text
        if (text.length > 1024) {
            // 分行的缓存只给了1024
            text = text.substring(0, 1024)
        }
        val context = mPageView?.context ?: return text
        return when (mJianFanMode) {
            MODE_JIAN_TO_FAN -> ChineseConverter.convert(text, ConversionType.S2T, context)
            MODE_FAN_TO_JIAN -> ChineseConverter.convert(text, ConversionType.T2S, context)
            else -> text
        }
    }

    /**
     * 获取字符串的 grapheme cluster 边界数组
     * 返回 [0, end1, end2, ..., text.length]
     */
    private fun getGraphemeBoundaries(text: String): IntArray {
        val boundaries = mutableListOf(0)
        if (text.isEmpty()) return boundaries.toIntArray()
        val bi = breakIteratorTl.get()!!
        bi.setText(text)
        bi.first()
        while (true) {
            val nxt = bi.next()
            if (nxt == android.icu.text.BreakIterator.DONE) break
            boundaries.add(nxt)
        }
        if (boundaries.last() != text.length) {
            boundaries.add(text.length)
        }
        return boundaries.toIntArray()
    }

    /**
     * 创建水平从左到右的行
     */
    private fun createTxtLineHLTR(lines: MutableList<TxtLine>, text: CharSequence, paint: TextPaint) {
        // 计算行高和字符宽度
        val fm = paint.fontMetrics
        val lineHeight = fm.bottom - fm.top
        val letterSpacing = paint.textSize * mDisplayParams.letterSpacing
        val charWidth = paint.measureText("啊")
        val sb = StringBuilder()
        val clusterLeft = FloatArray(1024)
        val clusterRight = FloatArray(1024)
        var lastExtX = 0f
        val createLine = { left: Float, end: Boolean ->
            val line = TxtLine(sb.toString(), paint === mTitlePaint, lineHeight, mDisplayParams.contentWidth, end)
            line.clusterBoundaries = getGraphemeBoundaries(line.txt)
            //两端对齐。
            val remWidth = (mDisplayParams.contentRight - left) % (charWidth + letterSpacing)
            if (remWidth > 0 && line.clusterBoundaries.size > 2) {
                val extX = if (end && lastExtX > 0) lastExtX else remWidth / (line.clusterBoundaries.size - 2)
                lastExtX = extX
                for (ci in 1 until line.clusterBoundaries.size - 1) {
                    val offset = ci * extX
                    clusterLeft[ci] = clusterLeft[ci] + offset
                    clusterRight[ci] = clusterRight[ci] + offset
                }
            }
            line.setLeftAndRight(clusterLeft, clusterRight, line.clusterBoundaries.size - 1)
            lines.add(line)
        }

        for (paragraph in text.lines()) {
            var trimmed = paragraph.trim()
            if (trimmed.isBlank()) {
                continue
            }
            // 添加缩进
            trimmed = "　　$trimmed"
            sb.clear()
            val allBounds = getGraphemeBoundaries(trimmed)
            var left = mDisplayParams.contentLeft
            var idx = 0
            for (i in 0 until allBounds.size - 1) {
                val s = allBounds[i]
                val e = allBounds[i + 1]
                val w = max(paint.measureText(trimmed, s, e), charWidth)
                if (left + letterSpacing + w > mDisplayParams.contentRight && sb.isNotEmpty()) {
                    createLine(left, false)
                    sb.clear()
                    left = mDisplayParams.contentLeft
                    idx = 0
                }
                //添加一个字符
                sb.append(trimmed, s, e)
                if (idx != 0) {
                    left += letterSpacing
                }
                clusterLeft[idx] = left
                clusterRight[idx] = left + w
                left += w
                idx++
            }
            if (sb.isNotEmpty()) {
                createLine(left, true)
            }

        }
    }

    /**
     * 创建水平从右到左的行
     */
    private fun createTxtLineHRTL(lines: MutableList<TxtLine>, text: CharSequence, paint: TextPaint) {
        val fm = paint.fontMetrics
        val lineHeight = fm.bottom - fm.top
        val letterSpacing = paint.textSize * mDisplayParams.letterSpacing
        val charWidth = paint.measureText("啊")
        val sb = StringBuilder()
        val clusterLeft = FloatArray(1024)
        val clusterRight = FloatArray(1024)
        var lastExtX = 0f
        val createLine = { right: Float, end: Boolean ->
            val line = TxtLine(sb.toString(), paint === mTitlePaint, lineHeight, mDisplayParams.contentWidth, end)
            line.clusterBoundaries = getGraphemeBoundaries(line.txt)
            //两端对齐。
            val remWidth = (right - mDisplayParams.contentLeft) % (charWidth + letterSpacing)
            if (remWidth > 0 && line.clusterBoundaries.size > 2) {
                val extX = if (end && lastExtX > 0) lastExtX else remWidth / (line.clusterBoundaries.size - 2)
                lastExtX = extX
                for (ci in 1 until line.clusterBoundaries.size - 1) {
                    val offset = ci * extX
                    clusterLeft[ci] = clusterLeft[ci] - offset
                    clusterRight[ci] = clusterRight[ci] - offset
                }
            }
            line.setLeftAndRight(clusterLeft, clusterRight, line.clusterBoundaries.size - 1)
            lines.add(line)
        }

        for (paragraph in text.lines()) {
            var trimmed = paragraph.trim()
            if (trimmed.isBlank()) {
                continue
            }
            // 添加缩进
            trimmed = "　　$trimmed"
            sb.clear()
            val allBounds = getGraphemeBoundaries(trimmed)
            var right = mDisplayParams.contentRight
            var idx = 0
            for (i in 0 until allBounds.size - 1) {
                val s = allBounds[i]
                val e = allBounds[i + 1]
                val w = max(paint.measureText(trimmed, s, e), charWidth)
                if (right - letterSpacing - w < mDisplayParams.contentLeft && sb.isNotEmpty()) {
                    createLine(right, false)
                    sb.clear()
                    right = mDisplayParams.contentRight
                    idx = 0
                }
                //添加一个字符
                sb.append(trimmed, s, e)
                if (idx != 0) {
                    right -= letterSpacing
                }
                clusterRight[idx] = right
                clusterLeft[idx] = right - w
                right -= w
                idx++
            }
            if (sb.isNotEmpty()) {
                createLine(right, true)
            }
        }
    }

    /**
     * 创建竖排从上到下
     */
    private fun createTxtLineVTTB(lines: MutableList<TxtLine>, text: CharSequence, paint: TextPaint) {
        val fm = paint.fontMetrics
        val charHeight = fm.bottom - fm.top
        val letterSpacing = paint.textSize * mDisplayParams.letterSpacing
        var lineWidth = paint.measureText("啊")
        val sb = StringBuilder()
        val clusterLeft = FloatArray(1024)
        val clusterRight = FloatArray(1024)
        var lastExtY = 0f
        val createLine = { top: Float, end: Boolean ->
            val line = TxtLine(sb.toString(), paint === mTitlePaint, mDisplayParams.contentHeight, lineWidth, end)
            line.clusterBoundaries = getGraphemeBoundaries(line.txt)
            //两端对齐。
            val remHeight = (mDisplayParams.contentBottom - top) % (charHeight + letterSpacing)
            if (remHeight > 0 && line.clusterBoundaries.size > 2) {
                val extX = if (end && lastExtY > 0) lastExtY else remHeight / (line.clusterBoundaries.size - 2)
                lastExtY = extX
                for (ci in 1 until line.clusterBoundaries.size - 1) {
                    val offset = ci * extX
                    clusterLeft[ci] = clusterLeft[ci] + offset
                    clusterRight[ci] = clusterRight[ci] + offset
                }
            }
            line.setLeftAndRight(clusterLeft, clusterRight, line.clusterBoundaries.size - 1)
            lines.add(line)
        }

        for (paragraph in text.lines()) {
            var trimmed = paragraph.trim()
            if (trimmed.isBlank()) {
                continue
            }
            // 添加缩进
            trimmed = "　　$trimmed"
            sb.clear()
            val allBounds = getGraphemeBoundaries(trimmed)
            var top = mDisplayParams.contentTop
            var idx = 0
            for (i in 0 until allBounds.size - 1) {
                val s = allBounds[i]
                val e = allBounds[i + 1]
                //竖排，字符宽度为行宽度，使用行字符最宽宽度作为行宽
                lineWidth = max(paint.measureText(trimmed, s, e), lineWidth)
                if (top + letterSpacing + charHeight > mDisplayParams.contentBottom && sb.isNotEmpty()) {
                    createLine(top, false)
                    sb.clear()
                    top = mDisplayParams.contentTop
                    idx = 0
                }
                //添加一个字符
                sb.append(trimmed, s, e)
                if (idx != 0) {
                    top += letterSpacing
                }
                clusterLeft[idx] = top
                clusterRight[idx] = top + charHeight
                top += charHeight
                idx++
            }
            if (sb.isNotEmpty()) {
                createLine(top, true)
            }
        }
    }

    /**
     * @return:获取初始显示的页面
     */
    private fun getCurPage(pos: Int): TxtPage {
        var pos = pos
        Log.i("获取书页：$pos")
        val list = this.curPageList
        if (list.isNullOrEmpty()) {
            Log.e("当前页列表为空")
            return TxtPage()
        }
        pos = min(pos, list.size - 1)
        pos = max(0, pos)
        Log.i("获取书页，调整后 pos:$pos")
        return list.get(pos)
    }

    private val prevPage: TxtPage?
        /**
         * @return:获取上一个页面
         */
        get() {
            if (mCurPage == null) {
                return null
            }
            val pos = mCurPage!!.position - 1
            if (pos < 0) {
                return null
            }
            return curPageList!![pos]
        }

    private val nextPage: TxtPage?
        get() {
            val txtPage = curPageList?.getOrNull((mCurPage?.position ?: return null) + 1)
            return txtPage
        }

    /**
     * @return:获取上一个章节的最后一页
     */
    private val prevLastPage: TxtPage? get() = curPageList?.lastOrNull()

    /**
     * 根据当前状态，决定是否能够翻页
     *
     * @return
     */
    private fun canTurnPage(): Boolean {
        if (!isChapterListPrepare) {
            return false
        }

        if (mStatus == STATUS_PARSE_ERROR || mStatus == STATUS_PARING) {
            return false
        } else if (mStatus == STATUS_ERROR) {
            mStatus = STATUS_LOADING
        }
        return true
    }

    fun hideSelectView(): Boolean {
        val helper = mSelectableTextHelper
        if (helper?.mSelectionInfo?.select != true) {
            return false
        }
        helper.resetSelectionInfo()
        helper.hideSelectView()
        mPageView!!.drawNextPage()
        return true
    }

    fun onLongPress(x: Float, y: Float) {
        if (isVerticalTypesetting) {
            return
        }
        Log.e("长按 x:$x y:$y")
        mSelectableTextHelper?.showSelectView(x, y)
    }

    override fun onTextSelected(info: SelectionInfo?) {
        Log.e("被选中的文字")
    }

    override fun onTextSelectedChange(info: SelectionInfo?) {
        mPageView!!.drawNextPage()
    }

    private inner class TxtLocationImpl : TxtLocation {
        override fun getLine(y: Float): Int {
            val page = this@PageLoader.mCurPage ?: return -1
            for (line in page.lines) {
                if (line.top <= y && line.bottom >= y) {
                    return line.index
                }
            }
            return -1
        }

        override fun getLineForOffset(offset: Int): Int {
            val page = this@PageLoader.mCurPage ?: return -1
            for (line in page.lines) {
                if (line.clusterStart <= offset && line.clusterStart + line.clusterBoundaries.size - 1 > offset) {
                    return line.index
                }
            }
            return page.lines.size - 1
        }

        override fun getLineStart(line: Int): Float {
            val page = this@PageLoader.mCurPage ?: return -1f
            val txtLine = page.lines[line]
            if (txtLine.txt.isBlank()) {
                return -1f
            }
            for (i in 0 until txtLine.clusterBoundaries.size - 2) {
                val isBlank = txtLine.txt.substring(txtLine.clusterBoundaries[i], txtLine.clusterBoundaries[i + 1]).isBlank()
                if (isBlank) {
                    continue
                }
                return txtLine.clusterLeft[i]
            }
            return -1f
        }

        override fun getLineStartOffset(line: Int): Int {
            val page = this@PageLoader.mCurPage ?: return -1
            val txtLine = page.lines[line]
            return txtLine.clusterStart
        }

        override fun getLineEnd(line: Int): Float {
            val page = this@PageLoader.mCurPage ?: return -1f
            val txtLine = page.lines[line]
            if (txtLine.txt.isBlank()) {
                return -1f
            }
            for (i in txtLine.clusterBoundaries.size - 2 downTo 0) {
                val isBlank = txtLine.txt.substring(txtLine.clusterBoundaries[i], txtLine.clusterBoundaries[i + 1]).isBlank()
                if (isBlank) {
                    continue
                }
                return txtLine.clusterRight[i]
            }
            return -1f
        }

        override fun getOffset(x: Float, y: Float): Int {
            val page = this@PageLoader.mCurPage ?: return -1
            val line = getLine(y)
            if (line == -1) {
                return -1
            }
            val txtLine = page.lines[line]
            val lineStart = getLineStart(txtLine.index)
            if (x < lineStart) {
                return -1
            }
            val lineEnd = getLineEnd(txtLine.index)
            if (x > lineEnd) {
                return -1
            }
            for (i in txtLine.clusterLeft.indices) {
                if (x >= txtLine.clusterLeft[i] && x <= txtLine.clusterRight[i]) {
                    return i + txtLine.clusterStart
                }
            }
            return -1
        }

        override fun getHysteresisOffset(x: Float, y: Float, oldOffset: Int, isLeft: Boolean): Int {
            val page = this@PageLoader.mCurPage ?: return oldOffset

            var difY = Int.MAX_VALUE.toFloat()
            var line = -1
            for (txtLine in page.lines) {
                if (!txtLine.txt.isBlank() && abs(txtLine.bottom - y) < difY) {
                    line = txtLine.index
                    difY = abs(txtLine.bottom - y)
                }
            }
            if (line == -1) {
                return oldOffset
            }
            val txtLine = page.lines[line]
            var difX = Int.MAX_VALUE.toFloat()
            var lineOffset = -1
            for (i in 0..<txtLine.clusterBoundaries.size - 1) {
                val isBlank = txtLine.txt.substring(txtLine.clusterBoundaries[i], txtLine.clusterBoundaries[i + 1]).isBlank()
                val clusterCenter = (txtLine.clusterLeft[i] + txtLine.clusterRight[i]) / 2
                if (!isBlank && abs(clusterCenter - x) < difX) {
                    lineOffset = i
                    difX = abs(clusterCenter - x)
                }
            }
            return if (lineOffset == -1) oldOffset else (lineOffset + txtLine.clusterStart)
        }

        override fun getHorizontalRight(offset: Int): Float {
            val page = this@PageLoader.mCurPage ?: return -1f
            for (line in page.lines) {
                if (line.clusterStart <= offset && line.clusterStart + line.clusterBoundaries.size - 1 > offset) {
                    val lineOffset = offset - line.clusterStart
                    return line.clusterRight[lineOffset]
                }
            }
            return -1f
        }

        override fun getHorizontalLeft(offset: Int): Float {
            val page = this@PageLoader.mCurPage ?: return -1f
            for (line in page.lines) {
                if (line.clusterStart <= offset && line.clusterStart + line.clusterBoundaries.size - 1 > offset) {
                    val lineOffset = offset - line.clusterStart
                    if (lineOffset == 0) {
                        return line.left
                    }

                    return line.clusterLeft[lineOffset]
                }
            }
            return -1f
        }

        override fun getLineTop(line: Int): Float {
            val page = this@PageLoader.mCurPage ?: return -1f
            val txtLine = page.lines[line]
            return txtLine.top
        }

        override fun getLineBottom(line: Int): Float {
            val page = this@PageLoader.mCurPage ?: return -1f
            val txtLine = page.lines[line]
            return txtLine.bottom
        }

        override fun getTxt(start: Int, end: Int): String {
            val page = this@PageLoader.mCurPage ?: return ""

            val startLine = page.lines[getLineForOffset(start)]
            val endLine = page.lines[getLineForOffset(end)]
            val startIdx = startLine.clusterBoundaries[start - startLine.clusterStart]
            val endIdx = endLine.clusterBoundaries[end - endLine.clusterStart + 1]
            if (startLine == endLine) {
                return startLine.txt.substring(startIdx, endIdx)
            }
            val stringBuilder = StringBuilder()
            for (i in startLine.index..endLine.index) {
                val line = page.lines[i]
                if (line == startLine) {
                    stringBuilder.append(line.txt.substring(startIdx))
                } else if (line == endLine) {
                    stringBuilder.append(line.txt.substring(0, endIdx))
                } else {
                    stringBuilder.append(line.txt)
                }
            }
            return stringBuilder.toString()
        }
    }


    /*****************************************interface */
    interface OnPageChangeListener {
        /**
         * 作用：请求加载章节内容
         *
         * @param requestChapters:需要下载的章节列表
         */
        fun requestChapters(requestChapters: MutableList<TxtChapter>)

        /**
         * 书籍阅读记录发生改变
         *
         * @param bean
         */
        fun onBookRecordChange(bean: BookRecordBean)
    }

    companion object {

        const val MODE_JIAN_FAN_OFF: Int = 0
        const val MODE_JIAN_TO_FAN: Int = 1
        const val MODE_FAN_TO_JIAN: Int = 2

        const val MODE_TYPESETTING_HORIZONTAL_LTR: Int = 0
        const val MODE_TYPESETTING_HORIZONTAL_RTL: Int = 1
        const val MODE_TYPESETTING_VERTICAL_LTR: Int = 2
        const val MODE_TYPESETTING_VERTICAL_RTL: Int = 3

        // 当前页面的状态
        const val STATUS_LOADING: Int = 1 // 正在加载
        const val STATUS_FINISH: Int = 2 // 加载完成
        const val STATUS_ERROR: Int = 3 // 加载错误 (一般是网络加载情况)
        const val STATUS_EMPTY: Int = 4 // 空数据
        const val STATUS_PARING: Int = 5 // 正在解析 (装载本地数据)
        const val STATUS_PARSE_ERROR: Int = 6 // 本地文件解析错误(暂未被使用)
        const val STATUS_CATEGORY_EMPTY: Int = 7 // 获取到的目录为空
    }

    fun setJianFanMode(mode: Int) {
        val normalized = when (mode) {
            MODE_JIAN_TO_FAN, MODE_FAN_TO_JIAN -> mode
            else -> MODE_JIAN_FAN_OFF
        }
        if (mJianFanMode == normalized) {
            return
        }
        mJianFanMode = normalized
        // 取消缓存
        ChapterPageCache.reset()

        // 如果当前已经显示数据
        if (isChapterListPrepare && mStatus == STATUS_FINISH) {
            // 重新计算当前页面
            dealLoadPageList(this.chapterPos)
            // 重新获取指定页面
            mCurPage = curPageList?.getOrNull(mCurPage?.position ?: -1) ?: curPageList?.lastOrNull()
        }

        mPageView!!.drawCurPage()
    }

    fun setTypesettingMode(mode: Int) {
        val normalized = when (mode) {
            MODE_TYPESETTING_HORIZONTAL_RTL,
            MODE_TYPESETTING_VERTICAL_LTR,
            MODE_TYPESETTING_VERTICAL_RTL -> mode

            else -> MODE_TYPESETTING_HORIZONTAL_LTR
        }
        if (mTypesettingMode == normalized) {
            return
        }
        mTypesettingMode = normalized
        // 先清缓存并重排，确保切换排版时分页立即更新。
        ChapterPageCache.reset()

        if (isChapterListPrepare && mStatus == STATUS_FINISH) {
            dealLoadPageList(this.chapterPos)
            mCurPage = curPageList?.getOrNull(mCurPage?.position ?: -1) ?: curPageList?.lastOrNull()
        }

        mPageView!!.drawCurPage()
    }
}
