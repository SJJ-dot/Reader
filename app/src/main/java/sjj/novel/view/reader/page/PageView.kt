package sjj.novel.view.reader.page

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.activity.viewModels
import com.sjianjun.reader.utils.act
import sjj.alog.Log
import sjj.novel.view.reader.animation.CoverPageAnim
import sjj.novel.view.reader.animation.HorizonPageAnim
import sjj.novel.view.reader.animation.NonePageAnim
import sjj.novel.view.reader.animation.PageAnimation
import sjj.novel.view.reader.animation.ScrollPageAnim
import sjj.novel.view.reader.animation.SimulationPageAnim
import sjj.novel.view.reader.animation.SlidePageAnim
import kotlin.math.abs

/**
 * Created by Administrator on 2016/8/29 0029.
 * 原作者的GitHub Project Path:(https://github.com/PeachBlossom/treader)
 * 绘制页面显示内容的类
 */
class PageView @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {
    private var mViewWidth = 0 // 当前View的宽
    private var mViewHeight = 0 // 当前View的高

    private var mStartX = 0
    private var mStartY = 0
    private var isMove = false

    // 初始化参数
    private var mBackground: Drawable? = null

    // 唤醒菜单的区域
    private var mCenterRect: RectF? = null
    var isPrepare: Boolean = false
        private set

    // 动画类
    private var mPageAnim: PageAnimation? = null

    //点击监听
    private var mTouchListener: TouchListener? = null
    var pageLoader: PageLoader? = null
        get() {
            if (field == null) {
                field = act?.viewModels<NetPageLoader>()?.value
                field?.initPageView(this)
            }
            return field
        }

    // 动画监听类
    private val mPageAnimListener: PageAnimation.OnPageChangeListener = object : PageAnimation.OnPageChangeListener {
        override fun hasPrev(): Boolean {
            return this@PageView.hasPrevPage()
        }

        override fun hasNext(): Boolean {
            return this@PageView.hasNextPage()
        }

        override fun pageCancel() {
            this@PageView.pageCancel()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mViewWidth = w
        mViewHeight = h

        isPrepare = true

        pageLoader?.prepareDisplay(w, h)
    }

    //设置翻页的模式
    fun setPageMode(pageMode: PageMode) {
        //视图未初始化的时候，禁止调用
        if (mViewWidth == 0 || mViewHeight == 0) return

        when (pageMode) {
            PageMode.SIMULATION -> mPageAnim = SimulationPageAnim(mViewWidth, mViewHeight, this, mPageAnimListener)
            PageMode.COVER -> mPageAnim = CoverPageAnim(mViewWidth, mViewHeight, this, mPageAnimListener)
            PageMode.SLIDE -> mPageAnim = SlidePageAnim(mViewWidth, mViewHeight, this, mPageAnimListener)
            PageMode.NONE -> mPageAnim = NonePageAnim(mViewWidth, mViewHeight, this, mPageAnimListener)
            PageMode.SCROLL -> mPageAnim = ScrollPageAnim(mViewWidth, mViewHeight, 0, pageLoader?.marginHeight ?: 0, this, mPageAnimListener)
        }
    }

    val nextBitmap: Bitmap? get() = mPageAnim?.getNextBitmap()
    val bgBitmap: Bitmap? get() = mPageAnim?.getBgBitmap()
    fun autoPrevPage(): Boolean {
        //滚动暂时不支持自动翻页
        if (mPageAnim is ScrollPageAnim) {
            return false
        } else {
            startPageAnim(PageAnimation.Direction.PRE)
            return true
        }
    }

    fun autoNextPage(): Boolean {
        if (mPageAnim is ScrollPageAnim) {
            return false
        } else {
            startPageAnim(PageAnimation.Direction.NEXT)
            return true
        }
    }

    private fun startPageAnim(direction: PageAnimation.Direction?) {
        if (mTouchListener == null) return
        //是否正在执行动画
        abortAnimation()
        if (direction == PageAnimation.Direction.NEXT) {
            val x = mViewWidth
            val y = mViewHeight
            //初始化动画
            mPageAnim!!.setStartPoint(x.toFloat(), y.toFloat())
            //设置点击点
            mPageAnim!!.setTouchPoint(x.toFloat(), y.toFloat())
            //设置方向
            val hasNext = hasNextPage()

            mPageAnim!!.setDirection(direction)
            if (!hasNext) {
                return
            }
        } else {
            val x = 0
            val y = mViewHeight
            //初始化动画
            mPageAnim!!.setStartPoint(x.toFloat(), y.toFloat())
            //设置点击点
            mPageAnim!!.setTouchPoint(x.toFloat(), y.toFloat())
            mPageAnim!!.setDirection(direction)
            //设置方向方向
            val hashPrev = hasPrevPage()
            if (!hashPrev) {
                return
            }
        }
        mPageAnim!!.startAnim()
        this.postInvalidate()
    }

    override fun setBackground(background: Drawable?) {
        mBackground = background
    }

    override fun onDraw(canvas: Canvas) {
        //绘制背景

        if (mBackground != null) {
            mBackground!!.draw(canvas)
        }
        if (mPageAnim != null) {
            //绘制动画
            mPageAnim!!.draw(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mTouchListener != null && mTouchListener!!.intercept(event)) {
            return true
        }

        val x = event.getX().toInt()
        val y = event.getY().toInt()
        when (event.getAction()) {
            MotionEvent.ACTION_DOWN -> {
                mHasPerformedLongPress = false
                mStartX = x
                mStartY = y
                isMove = false
                mPageAnim!!.onTouchEvent(event)
                postDelayed(longClick, ViewConfiguration.getLongPressTimeout().toLong())
            }

            MotionEvent.ACTION_MOVE -> {
                if (mHasPerformedLongPress) {
                    return true
                }
                // 判断是否大于最小滑动值。
                val slop = ViewConfiguration.get(getContext()).getScaledTouchSlop()
                if (!isMove) {
                    isMove = abs(mStartX - event.getX()) > slop || abs(mStartY - event.getY()) > slop
                }

                // 如果滑动了，则进行翻页。
                if (isMove) {
                    pageLoader?.hideSelectView()
                    mPageAnim!!.onTouchEvent(event)
                    removeCallbacks(longClick)
                }
            }

            MotionEvent.ACTION_UP -> {
                if (mHasPerformedLongPress) {
                    return true
                }
                removeCallbacks(longClick)
                if (!isMove) {
                    //设置中间区域范围
                    if (mCenterRect == null) {
                        mCenterRect = RectF(
                            (mViewWidth / 5).toFloat(), (mViewHeight / 3).toFloat(),
                            (mViewWidth * 4 / 5).toFloat(), (mViewHeight * 2 / 3).toFloat()
                        )
                    }
                    if (pageLoader?.hideSelectView() == true) {
                        return true
                    }

                    //是否点击了中间
                    if (mCenterRect!!.contains(x.toFloat(), y.toFloat())) {
                        if (mTouchListener != null) {
                            mTouchListener!!.center()
                        }
                        return true
                    }
                }
                mPageAnim!!.onTouchEvent(event)
            }

            MotionEvent.ACTION_CANCEL -> removeCallbacks(longClick)
        }
        return true
    }

    private var mHasPerformedLongPress = false
    private val longClick = Runnable {
        mHasPerformedLongPress = true
        pageLoader?.onLongPress(mStartX, mStartY)
    }

    /**
     * 判断是否存在上一页
     *
     * @return
     */
    private fun hasPrevPage(): Boolean {
        return pageLoader?.prev() ?: false
    }

    /**
     * 判断是否下一页存在
     *
     * @return
     */
    private fun hasNextPage(): Boolean {
        return pageLoader?.next() ?: false
    }

    private fun pageCancel() {
        pageLoader?.pageCancel()
    }

    override fun computeScroll() {
        //进行滑动
        if (mPageAnim != null) {
            mPageAnim!!.scrollAnim()
        }
        super.computeScroll()
    }

    //如果滑动状态没有停止就取消状态，重新设置Anim的触碰点
    fun abortAnimation() {
        mPageAnim!!.abortAnim()
    }

    val isRunning: Boolean get() = mPageAnim?.isRunning() == true

    fun setTouchListener(mTouchListener: TouchListener?) {
        this.mTouchListener = mTouchListener
    }

    fun drawNextPage() {
        if (!isPrepare) return

        if (mPageAnim is HorizonPageAnim) {
            (mPageAnim as HorizonPageAnim).changePage()
        }
        pageLoader?.drawPage(this.nextBitmap ?: return, false)
    }

    /**
     * 绘制当前页。
     *
     * @param isUpdate
     */
    fun drawCurPage(isUpdate: Boolean) {
        Log.i("绘制书籍内容 isPrepare:$isPrepare isUpdate:$isUpdate")
        if (!isPrepare) return

        if (!isUpdate) {
            if (mPageAnim is ScrollPageAnim) {
                (mPageAnim as ScrollPageAnim).resetBitmap()
            }
        }
        pageLoader?.drawPage(this.nextBitmap ?: return, isUpdate)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mPageAnim!!.abortAnim()
        mPageAnim!!.clear()

        pageLoader = null
        mPageAnim = null
    }

    interface TouchListener {
        fun intercept(event: MotionEvent?): Boolean

        fun center()
    }

    companion object {
        private const val TAG = "BookPageWidget"
    }
}
