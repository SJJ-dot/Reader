package sjj.novel.view.reader.page

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import com.jaeger.library.OnSelectListener
import com.jaeger.library.SelectableTextHelper
import com.jaeger.library.SelectionInfo
import com.jaeger.library.TxtLocation
import com.sjianjun.reader.BuildConfig
import io.reactivex.Single
import io.reactivex.SingleObserver
import io.reactivex.SingleTransformer
import io.reactivex.disposables.Disposable
import sjj.alog.Log
import sjj.novel.view.reader.bean.BookBean
import sjj.novel.view.reader.bean.BookRecordBean
import sjj.novel.view.reader.utils.RxUtils
import sjj.novel.view.reader.utils.ScreenUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Created by newbiechen on 17-7-1.
 */
abstract class PageLoader(pageView: PageView) : OnSelectListener {
    /**
     * 获取章节目录。
     *
     * @return
     */
    // 当前章节列表
    var chapterCategory: MutableList<TxtChapter>?
        protected set

    // 书本对象
    @JvmField
    protected var mCollBook: BookBean? = null

    // 监听器
    @JvmField
    protected var mPageChangeListener: OnPageChangeListener? = null

    private val mContext: Context = pageView.context

    // 页面显示类
    private var mPageView: PageView? = pageView

    // 当前显示的页
    private var mCurPage: TxtPage? = null
        set(value) {
            field = value
            if (BuildConfig.DEBUG)
                Log.e(
                    "设置当前页 mCurPage position:" + value?.position + " title:${value?.title}",
                    Exception()
                )
            saveRecord()
        }

    // 上一章的页面列表缓存
    private var mPrePageList: MutableList<TxtPage>? = null

    // 当前章节的页面列表
    var curPageList: MutableList<TxtPage>? = null
        private set(value) {
            field = value
            if (BuildConfig.DEBUG)
                Log.e(
                    "设置当前页列表 curPageList size:" + value?.size + " title:${value?.firstOrNull()?.title}",
                    Exception()
                )
            saveRecord()
        }

    // 下一章的页面列表缓存
    private var mNextPageList: MutableList<TxtPage>? = null

    // 绘制提示的画笔
    private var mTipPaint: Paint? = null

    // 绘制标题的画笔
    private var mTitlePaint: TextPaint? = null

    // 绘制背景颜色的画笔(用来擦除需要重绘的部分)
    private var mSelectedPaint: Paint? = null

    // 绘制小说内容的画笔
    private var mTextPaint: TextPaint? = null

    // 被遮盖的页，或者认为被取消显示的页
    private var mCancelPage: TxtPage? = null

    // 存储阅读记录类
    private var mBookRecord = BookRecordBean()

    private var mPreLoadDisp: Disposable? = null

    /*****************params */ // 当前的状态

    protected var mStatus: Int = STATUS_LOADING
        set(value) {
            field = value
            if (BuildConfig.DEBUG)
                Log.i("setPageStatus status:$value", Exception())
        }

    // 判断章节列表是否加载完成
    @JvmField
    protected var isChapterListPrepare: Boolean = false

    // 是否打开过章节
    var isChapterOpen: Boolean = false
        private set
    private var isFirstOpen = true
    var isClose: Boolean = false
        private set

    // 页面的翻页效果模式
    private var mPageMode: PageMode? = null

    //当前是否是夜间模式
    private val isNightMode = false
    private val mDisplayParams: DisplayParams

    //字体的颜色
    private var mTextColor = 0

    //标题的大小
    private var mTitleSize = 0f

    //字体的大小
    private var mTextSize = 0f

    //电池的百分比
    private var mBatteryLevel = 0

    //当前页面的背景
    private var mBackground: BgDrawable? = null

    /**
     * 获取当前章节的章节位置
     *
     * @return
     */
    // 当前章
    var chapterPos: Int = 0
        protected set(value) {
            field = value
            if (BuildConfig.DEBUG)
                Log.e("设置章节位置 chapterPos:$value", Exception())
        }


    //上一章的记录
    private var mLastChapterPos = 0
    private val screenUtils: ScreenUtils

    private val mSelectableTextHelper: SelectableTextHelper
    private val mLocation = TxtLocationImpl()

    /*****************************init params */
    init {
        mDisplayParams = DisplayParams()
        this.chapterCategory = ArrayList<TxtChapter>(1)
        screenUtils = ScreenUtils(mContext)
        mSelectableTextHelper = SelectableTextHelper.Builder(pageView, mLocation).build()
        mSelectableTextHelper.setSelectListener(this)
        // 初始化画笔
        initPaint()
        // 初始化PageView
        initPageView()
    }

    /**
     * 作用：设置与文字相关的参数
     *
     * @param textSize
     */
    private fun setUpTextParams(textSize: Float, lineSpace: Float) {
        // 文字大小
        mTextSize = textSize
        mDisplayParams.textInterval = mTextSize * lineSpace
        mDisplayParams.textPara = mTextSize * 1.5f

        mTitleSize = mTextSize * 1.1f
        mDisplayParams.titleInterval = mDisplayParams.textInterval
        mDisplayParams.titlePara = mTitleSize * 1.5f
    }

    fun setTypeface(typeface: Typeface?) {
        mTipPaint!!.setTypeface(typeface)
        mTextPaint!!.setTypeface(typeface)
        mTitlePaint!!.setTypeface(typeface)
        mPageView!!.drawCurPage(false)
    }

    private fun initPaint() {
        // 绘制提示的画笔
        mTipPaint = Paint()
        mTipPaint!!.setTextAlign(Paint.Align.LEFT) // 绘制的起始点
        mTipPaint!!.setTextSize(screenUtils.spToPx(12).toFloat()) // Tip默认的字体大小
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

    private fun initPageView() {
        //配置参数
        mPageView!!.setPageMode(mPageMode)
        mPageView!!.setBackground(mBackground)
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
        mPageView!!.drawCurPage(false)
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
        mPageView!!.drawCurPage(false)
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

        // 将上一章的缓存设置为null
        mPrePageList = null
        // 如果当前下一章缓存正在执行，则取消
        if (mPreLoadDisp != null) {
            mPreLoadDisp!!.dispose()
        }
        // 将下一章缓存设置为null
        mNextPageList = null

        // 打开指定章节
        openChapter()
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
        mPageView!!.drawCurPage(false)
        return true
    }

    /**
     * 翻到上一页
     *
     * @return
     */
    fun skipToPrePage(): Boolean {
        Log.i("skipToPrePage")
        return mPageView!!.autoPrevPage()
    }

    /**
     * 翻到下一页
     *
     * @return
     */
    fun skipToNextPage(): Boolean {
        Log.i("skipToNextPage")
        return mPageView!!.autoNextPage()
    }

    /**
     * 更新时间
     */
    fun updateTime() {
        Log.i("updateTime")
        if (!mPageView!!.isRunning()) {
            mPageView!!.drawCurPage(true)
        }
    }

    /**
     * 更新电量
     *
     * @param level
     */
    fun updateBattery(level: Int) {
        Log.i("updateBattery level:" + level)
        mBatteryLevel = level

        if (!mPageView!!.isRunning()) {
            mPageView!!.drawCurPage(true)
        }
    }

    /**
     * 设置提示的文字大小
     *
     * @param textSize:单位为 px。
     */
    fun setTipTextSize(textSize: Int) {
        Log.i("setTipTextSize")
        mTipPaint!!.setTextSize(textSize.toFloat())

        // 如果屏幕大小加载完成
        mPageView!!.drawCurPage(false)
    }

    /**
     * 设置文字相关参数
     *
     * @param textSize
     */
    fun setTextSize(textSize: Float, lineSpace: Float) {
        Log.i("setTextSize textSize:" + textSize + " lineSpace:" + lineSpace)
        // 设置文字相关参数
        setUpTextParams(textSize, lineSpace)

        // 设置画笔的字体大小
        mTextPaint!!.setTextSize(mTextSize)
        // 设置标题的字体大小
        mTitlePaint!!.setTextSize(mTitleSize)
        Log.i("字体大小：$mTextSize 标题大小:$mTitleSize")
        // 取消缓存
        mPrePageList = null
        mNextPageList = null

        // 如果当前已经显示数据
        if (isChapterListPrepare && mStatus == STATUS_FINISH) {
            // 重新计算当前页面
            dealLoadPageList(this.chapterPos)

            // 防止在最后一页，通过修改字体，以至于页面数减少导致崩溃的问题
            if (mCurPage!!.position >= curPageList!!.size) {
                mCurPage!!.position = curPageList!!.size - 1
            }

            // 重新获取指定页面
            mCurPage = curPageList!!.get(mCurPage!!.position)
        }

        mPageView!!.drawCurPage(false)
    }

    /**
     * 设置页面样式
     *
     * @param pageStyle:页面样式
     */
    fun setPageStyle(pageStyle: PageStyle) {
        Log.i("设置页面样式:$pageStyle")
        // 设置当前颜色样式
        mTextColor = pageStyle.getChapterContentColor(mContext)
        mBackground = BgDrawable(pageStyle.getBackground(mContext, 0, 0))

        mTipPaint!!.setColor(pageStyle.getLabelColor(mContext))
        mTitlePaint!!.setColor(pageStyle.getChapterTitleColor(mContext))
        mTextPaint!!.setColor(mTextColor)
        mSelectedPaint!!.setColor(pageStyle.getSelectedColor(mContext))

        mPageView!!.drawCurPage(false)
    }

    /**
     * 翻页动画
     *
     * @param pageMode:翻页模式
     * @see PageMode
     */
    fun setPageMode(pageMode: PageMode) {
        Log.i("setPageMode pageMode:$pageMode")
        mPageMode = pageMode

        mPageView!!.setPageMode(mPageMode)

        // 重新绘制当前页
        mPageView!!.drawCurPage(false)
    }

    /**
     * 设置内容与屏幕的间距
     *
     * @param marginWidth  :单位为 px
     * @param marginHeight :单位为 px
     */
    fun setPadding(paddingWidth: Int, marginHeight: Int) {
        Log.i("setPadding paddingWidth:$paddingWidth marginHeight:$marginHeight")
        mDisplayParams.paddingWidth = paddingWidth.toFloat()
        mDisplayParams.paddingHeight = marginHeight.toFloat()
        // 如果是滑动动画，则需要重新创建了
        if (mPageMode == PageMode.SCROLL) {
            mPageView!!.setPageMode(PageMode.SCROLL)
        }

        mPageView!!.drawCurPage(false)
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
            mPageView!!.drawCurPage(false)
        }

    var book: BookBean?
        /**
         * 获取书籍信息
         *
         * @return
         */
        get() = mCollBook
        set(book) {
            TxtChapter.evictAll()
            mCollBook = book
        }

    val pagePos: Int
        /**
         * 获取当前页的页码
         *
         * @return
         */
        get() = if (mCurPage == null) 0 else mCurPage!!.position

    val pageCount: Int
        get() = if (this.curPageList == null) 0 else curPageList!!.size

    val curChapter: TxtChapter?
        get() {
            Log.i("getCurChapter mCurChapterPos:" + this.chapterPos)
            return chapterCategory?.getOrNull(chapterPos)
        }

    val marginHeight: Int
        /**
         * 获取距离屏幕的高度
         *
         * @return
         */
        get() = Math.round(mDisplayParams.tipHeight)

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
        //        if (curChapterPos == bookRecord.chapter && bookRecord.pagePos == curPage.position) {
//            return;
//        }
        bookRecord.bookId = collBook.id
        bookRecord.chapter = curChapterPos
        bookRecord.pagePos = curPage.position

        bookRecord.isEnd =
            curChapterPos == chapterList.size - 1 && curPageList.size == curPage.position + 1
        mPageChangeListener!!.onBookRecordChange(bookRecord)
    }

    fun setBookRecord(record: BookRecordBean) {
        Log.i("设置阅读记录")
        mBookRecord = record
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
            mPageView!!.drawCurPage(false)
            return
        }

        // 如果获取到的章节目录为空
        if (chapterCategory!!.isEmpty()) {
            Log.e("章节为空")
            mStatus = STATUS_CATEGORY_EMPTY
            mPageView!!.drawCurPage(false)
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
                mCurPage = getCurPage(position)
                mCancelPage = mCurPage
                // 切换状态
                isChapterOpen = true
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

        mPageView!!.drawCurPage(false)
    }

    fun chapterError() {
        //加载错误
        mStatus = STATUS_ERROR
        mPageView!!.drawCurPage(false)
    }

    /**
     * 关闭书本
     */
    fun closeBook() {
        isChapterOpen = false
        isChapterListPrepare = false
        isClose = true

        if (mPreLoadDisp != null) {
            mPreLoadDisp!!.dispose()
        }

        clearList(this.chapterCategory)
        clearList(this.curPageList)
        clearList(mNextPageList)

        this.chapterCategory = null
        this.curPageList = null
        mNextPageList = null
//        mPageView = null
        mCurPage = null
    }

    private fun clearList(list: MutableList<*>?) {
        if (list != null) {
            list.clear()
        }
    }

    /**
     * 加载页面列表
     *
     * @param chapterPos:章节序号
     * @return
     */
    @Throws(Exception::class)
    private fun loadPageList(chapterPos: Int): MutableList<TxtPage>? {
        // 获取章节
        val chapter = chapterCategory?.getOrNull(chapterPos) ?: return null
        // 判断章节是否存在
        if (!hasChapterData(chapter)) {
            Log.e("章节数据不存在 chapterPos:" + chapterPos + " title:" + chapter.title)
            return null
        }
        // 获取章节的文本流
        val chapters = loadPages(chapter)
        return chapters
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
    fun drawPage(bitmap: Bitmap, isUpdate: Boolean) {
        drawBackground(mPageView!!.getBgBitmap(), isUpdate)
        if (!isUpdate) {
            drawContent(bitmap)
        }
        Log.i("书籍绘制完成")
        //更新绘制
        mPageView!!.invalidate()
    }

    private fun drawBackground(bitmap: Bitmap, isUpdate: Boolean) {
        Log.i("绘制背景 Width:" + bitmap.getWidth() + " Height:" + bitmap.getHeight())
        val canvas = Canvas(bitmap)
        val tipMarginHeight = screenUtils.dpToPx(3)
        if (!isUpdate) {
            /****绘制背景 */
            if (mBackground != null) {
                mBackground!!.draw(canvas)
            } else {
                Log.e("没设置背景？")
            }

            if (chapterCategory?.isEmpty() == false) {
                /*****初始化标题的参数 */
                //需要注意的是:绘制text的y的起始点是text的基准线的位置，而不是从text的头部的位置
                val tipTop =
                    mDisplayParams.tipHeight / 2 + (mTipPaint!!.getFontMetrics().bottom - mTipPaint!!.getFontMetrics().top) / 2
                //根据状态不一样，数据不一样
                if (mStatus != STATUS_FINISH) {
                    if (isChapterListPrepare) {
                        canvas.drawText(
                            chapterCategory!!.get(this.chapterPos).title,
                            mDisplayParams.contentLeft,
                            tipTop,
                            mTipPaint!!
                        )
                    }
                } else {
                    /******绘制页码 */
                    val percent = (mCurPage!!.position + 1).toString() + "/" + curPageList!!.size
                    canvas.drawText(
                        percent,
                        mDisplayParams.contentRight - mTipPaint!!.measureText(percent),
                        tipTop,
                        mTipPaint!!
                    )

                    val count = mTipPaint!!.breakText(
                        mCurPage!!.title,
                        true,
                        mDisplayParams.contentRight - mTipPaint!!.measureText(percent) - tipMarginHeight,
                        null
                    )
                    canvas.drawText(
                        mCurPage!!.title,
                        0,
                        count,
                        mDisplayParams.contentLeft,
                        tipTop,
                        mTipPaint!!
                    )
                }
            }
        } else {
            throw UnsupportedOperationException("不支持绘制时间。后续再改")
        }
    }

    private fun drawContent(bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        Log.i("绘制书籍内容 w:" + bitmap.getWidth() + " h:" + bitmap.getHeight() + " mStatus:" + mStatus)
        if (mPageMode == PageMode.SCROLL) {
            if (mBackground != null) {
                mBackground!!.draw(canvas)
            }
        }

        //        if (BuildConfig.DEBUG) {
//            canvas.drawLine(mDisplayParams.getContentLeft(), 0f, mDisplayParams.getContentLeft(), mDisplayParams.getHeight(), mTitlePaint);
//            canvas.drawLine(mDisplayParams.getContentRight(), 0f, mDisplayParams.getContentRight(), mDisplayParams.getHeight(), mTitlePaint);
//
//            canvas.drawLine(0f, mDisplayParams.getContentTop(), mDisplayParams.getWidth(), mDisplayParams.getContentTop(), mTitlePaint);
//            canvas.drawLine(0f, mDisplayParams.getContentBottom(), mDisplayParams.getWidth(), mDisplayParams.getContentBottom(), mTitlePaint);
//        }
        /******绘制内容 */
        if (mStatus != STATUS_FINISH) {
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
            if (mSelectableTextHelper.mSelectionInfo.select) {
                val start = mSelectableTextHelper.mSelectionInfo.start
                val end = mSelectableTextHelper.mSelectionInfo.end

                val startLine = mLocation.getLineForOffset(start)
                val endLine = mLocation.getLineForOffset(end)
                //                Log.e("startLine:" + startLine + " endLine:" + endLine + " start:" + start + " end:" + end);
                for (i in startLine..endLine) {
                    val txtLine = mCurPage!!.lines.get(i)
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

            val top: Float

            if (mPageMode == PageMode.SCROLL) {
                top = -mTextPaint!!.getFontMetrics().top
            } else {
                top = mDisplayParams.tipHeight - mTextPaint!!.getFontMetrics().top
            }
            val titleMetrics = mTitlePaint!!.getFontMetrics()
            val titleBase = -titleMetrics.ascent
            val textMetrics = mTextPaint!!.getFontMetrics()
            val textBase = -textMetrics.ascent
            for (line in mCurPage!!.lines) {
                val y = line.top + (if (line.isTitle) titleBase else textBase)
                val paint: Paint = (if (line.isTitle) mTitlePaint else mTextPaint)!!
                for (i in 0..<line.txt.length) {
                    canvas.drawText(line.txt, i, i + 1, line.charLeft[i], y, paint)
                }

                //                canvas.drawText(line.txt, line.left, y, paint);

//                if (BuildConfig.DEBUG) {
//                    canvas.drawLine(line.left, line.top, line.right, line.top, mTitlePaint);
//                    canvas.drawLine(line.left, line.bottom, line.right, line.bottom, mTitlePaint);
//                }
            }
        }
    }

    fun prepareDisplay(w: Int, h: Int) {
        // 获取PageView的宽高
        mDisplayParams.width = w
        mDisplayParams.height = h
        // 获取内容显示位置的大小
        // 重置 PageMode
        mPageView!!.setPageMode(mPageMode)

        if (!isChapterOpen) {
            // 展示加载界面
            mPageView!!.drawCurPage(false)
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
            mPageView!!.drawCurPage(false)
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

        // 当前章缓存为下一章
        mNextPageList = this.curPageList

        // 判断是否具有上一章缓存
        if (mPrePageList != null) {
            this.curPageList = mPrePageList
            mPrePageList = null

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
        dealLoadPageList(this.chapterPos)
        preLoadNextChapter()
        // 重新设置文章指针的位置
        mCurPage = getCurPage(mCurPage?.position ?: 0)
        mPageView!!.drawCurPage(false)
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

        // 将当前章的页面列表，作为上一章缓存
        mPrePageList = this.curPageList

        // 是否下一章数据已经预加载了
        if (mNextPageList != null) {
            this.curPageList = mNextPageList
            mNextPageList = null
        } else {
            // 处理页面解析
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

                    // 添加一个空数据
                    val page = TxtPage()
                    curPageList!!.add(page)
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
    fun preLoadNextChapter() {
        val nextChapter = this.chapterPos + 1

        // 如果不存在下一章，且下一章没有数据，则不进行加载。
        if (!hasNextChapter() || !hasChapterData(chapterCategory?.getOrNull(nextChapter))) {
            return
        }

        //如果之前正在加载则取消
        if (mPreLoadDisp != null) {
            mPreLoadDisp!!.dispose()
        }

        //调用异步进行预加载加载
        Single.create { e ->
            val pages = loadPageList(nextChapter)
            if (pages == null) {
                e.onError(Exception("页面加载失败"))
            } else {
                e.onSuccess(pages)
            }
        }.compose(SingleTransformer { upstream: Single<MutableList<TxtPage>> ->
            RxUtils.toSimpleSingle(upstream)
        }).subscribe(object : SingleObserver<MutableList<TxtPage>?> {
            override fun onSubscribe(d: Disposable) {
                mPreLoadDisp = d
            }

            override fun onSuccess(pages: MutableList<TxtPage>) {
                mNextPageList = pages
            }

            override fun onError(e: Throwable) {
                //无视错误
            }
        })
    }

    // 取消翻页
    fun pageCancel() {
        if (mCurPage!!.position == 0 && this.chapterPos > mLastChapterPos) { // 加载到下一章取消了
            if (mPrePageList != null) {
                cancelNextChapter()
            } else {
                if (parsePrevChapter()) {
                    mCurPage = this.prevLastPage
                } else {
                    mCurPage = TxtPage()
                }
            }
        } else if (this.curPageList == null || (mCurPage!!.position == curPageList!!.size - 1 && this.chapterPos < mLastChapterPos)) {  // 加载上一章取消了

            if (mNextPageList != null) {
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

        mNextPageList = this.curPageList
        this.curPageList = mPrePageList
        mPrePageList = null

        mCurPage = this.prevLastPage
        mCancelPage = null
    }

    private fun cancelPreChapter() {
        // 重置位置点
        val temp = mLastChapterPos
        mLastChapterPos = this.chapterPos
        this.chapterPos = temp
        // 重置页面列表
        mPrePageList = this.curPageList
        this.curPageList = mNextPageList
        mNextPageList = null

        mCurPage = getCurPage(0)
        mCancelPage = null
    }

    /**************************************private method */
    /**
     * 将章节数据，解析成页面列表
     *
     * @param chapter ：章节信息
     * @param br      ：章节的文本流
     * @return
     */
    private fun loadPages(chapter: TxtChapter): MutableList<TxtPage> {
        Log.i("加载章节内容：" + chapter.title + " pos:" + this.chapterPos)
        //生成的页面
        val pages: MutableList<TxtPage> = ArrayList()
        //使用流的方式加载
        val lines: MutableList<TxtLine> = ArrayList()

        createTxtLine(lines, chapter.title, mTitlePaint!!)
        val titleLines = lines.size
        createTxtLine(lines, chapter.content, mTextPaint!!)
        val pageLine: MutableList<TxtLine> = ArrayList()
        var i = 0
        while (i < lines.size) {
            var top = mDisplayParams.contentTop
            if (i == 0) {
                top += mDisplayParams.titlePara
            }
            var nextCharStart = 0
            while (i < lines.size) {
                val line = lines.get(i)
                val remHeight = mDisplayParams.contentBottom - top - line.height
                if (remHeight < 0) {
                    break
                }
                line.left = mDisplayParams.contentLeft
                line.top = top
                line.right = mDisplayParams.contentRight
                line.bottom = top + line.height

                line.index = pageLine.size
                line.charStart = nextCharStart
                nextCharStart += line.txt.length
                pageLine.add(line)
                //                Log.e(line.txt + "-" + line.txt.endsWith("\n") + "-" + line.isTitle);
                i++
                top += line.height
                if (line.isTitle) {
                    if (line.index == titleLines - 1) {
                        top += mDisplayParams.titlePara
                    } else {
                        top += mDisplayParams.titleInterval
                    }
                } else {
                    if (line.isParaEnd) {
                        top += mDisplayParams.textPara
                    } else {
                        top += mDisplayParams.textInterval
                    }
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

    private fun createTxtLine(lines: MutableList<TxtLine>, text: CharSequence, paint: TextPaint) {
        var text = text
        val sb = StringBuilder()
        for (line in text.lines()) {
            var line = line
            line = line.trim()
            if (!line.isEmpty()) {
                sb.append("　　").append(line).append(System.lineSeparator())
            }
        }
        text = sb.trimEnd()

        val layout = StaticLayout.Builder.obtain(
            text,
            0,
            text.length,
            paint,
            Math.round(mDisplayParams.contentWidth)
        ).build()
        for (i in 0..<layout.getLineCount()) {
            val left = layout.getLineLeft(i)
            val right = layout.getLineRight(i)
            val lineStart = layout.getLineStart(i)
            var lineEnd = layout.getLineEnd(i)
            val lineHeight = layout.getLineBottom(i) - layout.getLineTop(i)
            var lineStr = layout.getText().subSequence(lineStart, lineEnd)
            if (lineStr.isBlank()) {
                continue
            }
            val isParaEnd = lineStr.get(lineStr.length - 1) == '\n'
            lineStr = lineStr.trimEnd().toString()
            lineEnd = lineStart + lineStr.length
            val line = TxtLine(
                lineStr.toString(),
                paint === mTitlePaint,
                lineHeight.toFloat(),
                right - left,
                isParaEnd
            )
            lines.add(line)
            for (offset in lineStart..<lineEnd) {
                val leftOf = layout.getPrimaryHorizontal(offset)
                val rightOf =
                    if (offset == lineEnd - 1) right else layout.getPrimaryHorizontal(offset + 1)
                line.setLeftOfRight(
                    offset - lineStart,
                    leftOf + mDisplayParams.contentLeft,
                    rightOf + mDisplayParams.contentLeft
                )
            }


            var extX = 0f
            var len = line.txt.length
            var st = 0

            while ((st < len) && (line.txt.get(st).isWhitespace())) {
                st++
            }
            while ((st < len) && (line.txt.get(len - 1).isWhitespace())) {
                len--
            }
            if (len - st <= 1) {
                continue
            }

            var maxCharWidth = 0f
            for (idx in line.charLeft.indices) {
                maxCharWidth = max(line.charRight[idx] - line.charLeft[idx], maxCharWidth)
            }

            var remWidth = mDisplayParams.contentWidth - line.width
            if (remWidth > maxCharWidth * 2) {
                remWidth = maxCharWidth
            }

            extX = remWidth / (len - st - 1)
            extX = min(extX, maxCharWidth / 8f)

            for (idx in line.charLeft.indices) {
                val offsetX = max((idx - st), 0) * extX
                line.setLeftOfRight(
                    idx,
                    offsetX + line.charLeft[idx],
                    offsetX + line.charRight[idx]
                )
            }
        }
    }

    /**
     * @return:获取初始显示的页面
     */
    private fun getCurPage(pos: Int): TxtPage? {
        var pos = pos
        Log.i("获取书页：$pos")
        val list = this.curPageList
        if (list.isNullOrEmpty()) {
            Log.e("当前页列表为空")
            return TxtPage()
        }
        pos = min(pos, list.size - 1)
        pos = max(0, pos)
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
            if (BuildConfig.DEBUG)
                Log.i(
                    "获取下一页 mCurPage title:${mCurPage?.title} postion:${mCurPage?.position} nextPage title:${txtPage?.title} postion:${txtPage?.position}",
                    Exception()
                )
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
        if (!mSelectableTextHelper.mSelectionInfo.select) {
            return false
        }
        mSelectableTextHelper.resetSelectionInfo()
        mSelectableTextHelper.hideSelectView()
        mPageView!!.drawNextPage()
        return true
    }

    fun onLongPress(x: Int, y: Int) {
        Log.e("长按 x:" + x + " y:" + y)
        mSelectableTextHelper.showSelectView(x, y)
    }

    override fun onTextSelected(info: SelectionInfo?) {
        Log.e("被选中的文字")
    }

    override fun onTextSelectedChange(info: SelectionInfo?) {
        mPageView!!.drawNextPage()
    }

    private inner class TxtLocationImpl : TxtLocation {
        override fun getLine(y: Float): Int {
            val page = this@PageLoader.mCurPage
            if (page == null) {
                return -1
            }
            for (line in page.lines) {
                if (line.top <= y && line.bottom >= y) {
                    return line.index
                }
            }
            return -1
        }

        override fun getLineForOffset(offset: Int): Int {
            val page = this@PageLoader.mCurPage
            if (page == null) {
                return -1
            }
            for (line in page.lines) {
                if (line.charStart <= offset && line.charStart + line.txt.length > offset) {
                    return line.index
                }
            }
            return page.lines.size - 1
        }

        override fun getLineStart(line: Int): Float {
            val page = this@PageLoader.mCurPage
            if (page == null) {
                return -1f
            }
            val txtLine = page.lines.get(line)
            if (txtLine.txt.isBlank()) {
                return -1f
            }
            for (i in 0..<txtLine.txt.length) {
                if (Character.isWhitespace(txtLine.txt.get(i))) {
                    continue
                }
                return getHorizontalLeft(txtLine.charStart + i)
            }
            return -1f
        }

        override fun getLineStartOffset(line: Int): Int {
            val page = this@PageLoader.mCurPage
            if (page == null) {
                return -1
            }
            val txtLine = page.lines.get(line)
            return txtLine.charStart
        }

        override fun getLineEnd(line: Int): Float {
            val page = this@PageLoader.mCurPage
            if (page == null) {
                return -1f
            }
            val txtLine = page.lines.get(line)
            if (txtLine.txt.isBlank()) {
                return -1f
            }
            for (i in txtLine.txt.length - 1 downTo 0) {
                if (Character.isWhitespace(txtLine.txt.get(i))) {
                    continue
                }
                return getHorizontalRight(txtLine.charStart + i)
            }
            return -1f
        }

        override fun getOffset(x: Float, y: Float): Int {
            val page = this@PageLoader.mCurPage
            if (page == null) {
                return -1
            }
            val line = getLine(y)
            if (line == -1) {
                return -1
            }
            val txtLine = page.lines.get(line)
            val paint: Paint?
            if (txtLine.isTitle) {
                paint = mTitlePaint
            } else {
                paint = mTextPaint
            }
            //            Log.e(txtLine);
//            Log.e(page.lines.get(line + 1));
//            Log.e("len:" + txtLine.txt.length() + ">>" + txtLine.txt);
            val lineStart = getLineStart(txtLine.index)
            if (x < lineStart) {
                return -1
            }
            val lineEnd = getLineEnd(txtLine.index)
            if (x > lineEnd) {
                return -1
            }
            for (i in txtLine.charLeft.indices) {
                if (x >= txtLine.charLeft[i] && x <= txtLine.charRight[i]) {
                    return i + txtLine.charStart
                }
            }
            return -1
        }

        override fun getHysteresisOffset(x: Float, y: Float, oldOffset: Int, isLeft: Boolean): Int {
            val page = this@PageLoader.mCurPage
            if (page == null) {
                return oldOffset
            }

            var difY = Int.Companion.MAX_VALUE.toFloat()
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
            val txtLine = page.lines.get(line)
            //            Log.e(txtLine);
            var difX = Int.Companion.MAX_VALUE.toFloat()
            var lineOffset = -1
            for (i in 0..<txtLine.txt.length) {
                if (!txtLine.txt.get(i)
                        .isWhitespace() && abs((txtLine.charLeft[i] + txtLine.charRight[i]) / 2 - x) < difX
                ) {
                    lineOffset = i
                    difX = abs((txtLine.charLeft[i] + txtLine.charRight[i]) / 2 - x)
                }
            }
            return if (lineOffset == -1) oldOffset else (lineOffset + txtLine.charStart)
        }

        fun getNotWhitespace(line: Int, isLeft: Boolean): Int {
            val page = this@PageLoader.mCurPage
            if (page == null) {
                return -1
            }
            if (line < 0 || line >= page.lines.size) {
                return -1
            }
            val txtLine = page.lines.get(line)
            if (txtLine.txt.isBlank()) {
                if (isLeft) {
                    return getNotWhitespace(line + 1, isLeft)
                } else {
                    return getNotWhitespace(line - 1, isLeft)
                }
            } else {
                if (isLeft) {
                    for (i in 0..<txtLine.txt.length) {
                        if (Character.isWhitespace(txtLine.txt.get(i))) {
                            continue
                        }
                        return txtLine.charStart + i
                    }
                } else {
                    for (i in txtLine.txt.length - 1 downTo 0) {
                        if (Character.isWhitespace(txtLine.txt.get(i))) {
                            continue
                        }
                        return txtLine.charStart + i
                    }
                }
            }
            return -1
        }

        override fun getHorizontalRight(offset: Int): Float {
            val page = this@PageLoader.mCurPage
            if (page == null) {
                return -1f
            }
            for (line in page.lines) {
                if (line.charStart <= offset && line.charStart + line.txt.length > offset) {
                    val lineOffset = offset - line.charStart
                    return line.charRight[lineOffset]
                }
            }
            return -1f
        }

        override fun getHorizontalLeft(offset: Int): Float {
            val page = this@PageLoader.mCurPage
            if (page == null) {
                return -1f
            }
            for (line in page.lines) {
                if (line.charStart <= offset && line.charStart + line.txt.length > offset) {
                    val lineOffset = offset - line.charStart
                    if (lineOffset == 0) {
                        return line.left
                    }

                    return line.charLeft[lineOffset]
                }
            }
            return -1f
        }

        override fun getLineTop(line: Int): Float {
            val page = this@PageLoader.mCurPage
            if (page == null) {
                return -1f
            }
            val txtLine = page.lines.get(line)
            return txtLine.top
        }

        override fun getLineBottom(line: Int): Float {
            val page = this@PageLoader.mCurPage
            if (page == null) {
                return -1f
            }
            val txtLine = page.lines.get(line)
            return txtLine.bottom
        }

        override fun getTxt(start: Int, end: Int): String {
            val page = this@PageLoader.mCurPage
            if (page == null) {
                return ""
            }

            val startLine = page.lines.get(getLineForOffset(start))
            val endLine = page.lines.get(getLineForOffset(end))
            if (startLine == endLine) {
                return startLine.txt.substring(
                    start - startLine.charStart,
                    end - startLine.charStart + 1
                )
            }
            val stringBuilder = StringBuilder()
            for (i in startLine.index..endLine.index) {
                val line = page.lines.get(i)
                if (line == startLine) {
                    stringBuilder.append(line.txt.substring(start - line.charStart))
                } else if (line == endLine) {
                    stringBuilder.append(line.txt.substring(0, end - line.charStart + 1))
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
        private const val TAG = "PageLoader"

        // 当前页面的状态
        const val STATUS_LOADING: Int = 1 // 正在加载
        const val STATUS_FINISH: Int = 2 // 加载完成
        const val STATUS_ERROR: Int = 3 // 加载错误 (一般是网络加载情况)
        const val STATUS_EMPTY: Int = 4 // 空数据
        const val STATUS_PARING: Int = 5 // 正在解析 (装载本地数据)
        const val STATUS_PARSE_ERROR: Int = 6 // 本地文件解析错误(暂未被使用)
        const val STATUS_CATEGORY_EMPTY: Int = 7 // 获取到的目录为空
    }
}
