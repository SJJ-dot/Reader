package com.jaeger.library

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnScrollChangedListener
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.annotation.ColorInt
import com.sjianjun.reader.R
import sjj.alog.Log
import java.net.URLEncoder
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.core.net.toUri
import com.sjianjun.reader.view.click

/**
 * Created by Jaeger on 16/8/30.
 *
 *
 * Email: chjie.jaeger@gmail.com
 * GitHub: https://github.com/laobie
 */
class SelectableTextHelper(builder: Builder) {
    private var mStartHandle: CursorHandle? = null
    private var mEndHandle: CursorHandle? = null
    private var mOperateWindow: OperateWindow? = null
    var mSelectionInfo: SelectionInfo = SelectionInfo()
    private var mSelectListener: OnSelectListener? = null

    private val mContext: Context
    private val mView: View
    private val mLocation: TxtLocation

    private val mSelectedColor: Int
    private val mCursorHandleColor: Int
    private val mCursorHandleSize: Int
    private var isHideWhenScroll = false

    private var mOnPreDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    var mOnScrollChangedListener: OnScrollChangedListener? = null

    private fun init() {
        mView.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
            }

            override fun onViewDetachedFromWindow(v: View) {
                destroy()
            }
        })

        mOnPreDrawListener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (isHideWhenScroll) {
                    isHideWhenScroll = false
                    postShowSelectView(DEFAULT_SHOW_DURATION)
                }
                return true
            }
        }
        mView.getViewTreeObserver().addOnPreDrawListener(mOnPreDrawListener)

        mOnScrollChangedListener = object : OnScrollChangedListener {
            override fun onScrollChanged() {
                if (!isHideWhenScroll && !mSelectionInfo.select) {
                    isHideWhenScroll = true
                    if (mOperateWindow != null) {
                        mOperateWindow!!.dismiss()
                    }
                    if (mStartHandle != null) {
                        mStartHandle!!.dismiss()
                    }
                    if (mEndHandle != null) {
                        mEndHandle!!.dismiss()
                    }
                }
            }
        }
        mView.getViewTreeObserver().addOnScrollChangedListener(mOnScrollChangedListener)

        mOperateWindow = OperateWindow(mContext)
    }

    private fun postShowSelectView(duration: Int) {
        mView.removeCallbacks(mShowSelectViewRunnable)
        if (duration <= 0) {
            mShowSelectViewRunnable.run()
        } else {
            mView.postDelayed(mShowSelectViewRunnable, duration.toLong())
        }
    }

    private val mShowSelectViewRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!mSelectionInfo.select) return
            if (mOperateWindow != null) {
                mOperateWindow!!.show()
            }
            if (mStartHandle != null) {
                showCursorHandle(mStartHandle!!)
            }
            if (mEndHandle != null) {
                showCursorHandle(mEndHandle!!)
            }
        }
    }

    init {
        mView = builder.mView
        mLocation = builder.mLocation
        mContext = mView.getContext()
        mSelectedColor = builder.mSelectedColor
        mCursorHandleColor = builder.mCursorHandleColor
        mCursorHandleSize = TextLayoutUtil.dp2px(mContext, builder.mCursorHandleSizeInDp)
        init()
    }

    fun hideSelectView() {
        mSelectionInfo.select = false
        if (mStartHandle != null) {
            mStartHandle!!.dismiss()
        }
        if (mEndHandle != null) {
            mEndHandle!!.dismiss()
        }
        if (mOperateWindow != null) {
            mOperateWindow!!.dismiss()
        }
    }

    fun resetSelectionInfo() {
        mSelectionInfo.select = false
    }

    fun showSelectView(x: Int, y: Int) {
        hideSelectView()
        resetSelectionInfo()
        if (mStartHandle == null) mStartHandle = CursorHandle(true)
        if (mEndHandle == null) mEndHandle = CursorHandle(false)

        val startOffset = mLocation.getOffset(x.toFloat(), y.toFloat())
        if (startOffset < 0) {
            return
        }
        val endOffset = startOffset
        if (mLocation.getTxt(startOffset, endOffset).isBlank()) {
            return
        }

        selectText(startOffset, endOffset)
        showCursorHandle(mStartHandle!!)
        showCursorHandle(mEndHandle!!)
        mOperateWindow!!.show()
    }

    private fun showCursorHandle(cursorHandle: CursorHandle) {
        val offset = if (cursorHandle.isLeft) mSelectionInfo.start else mSelectionInfo.end
        val line = mLocation.getLineForOffset(offset)
        val x = if (cursorHandle.isLeft) mLocation.getHorizontalLeft(offset) else mLocation.getHorizontalRight(offset)
        cursorHandle.show(x, mLocation.getLineBottom(line))
    }

    private fun selectText(startPos: Int, endPos: Int) {
        val oldS = mSelectionInfo.start
        val oldE = mSelectionInfo.end
        if (startPos != -1) {
            mSelectionInfo.start = startPos
        }
        if (endPos != -1) {
            mSelectionInfo.end = endPos
        }
        if (mSelectionInfo.start > mSelectionInfo.end) {
            val temp = mSelectionInfo.start
            mSelectionInfo.start = mSelectionInfo.end
            mSelectionInfo.end = temp
        }
        //        Log.e(mSelectionInfo + mLocation.getTxt(mSelectionInfo.start, mSelectionInfo.end));
        if (oldS != mSelectionInfo.start || oldE != mSelectionInfo.end || !mSelectionInfo.select) {
            mSelectionInfo.select = true
            mSelectListener!!.onTextSelectedChange(mSelectionInfo)
        }
        //        int[] offset = checkOffset(mLocation, mSelectionInfo.start, mSelectionInfo.end);
//        mSelectionInfo.start = offset[0];
//        mSelectionInfo.end = offset[1];
//
//        mSelectionInfo.mSelectionContent = mLocation.getText().subSequence(mSelectionInfo.start, mSelectionInfo.end).toString();
//        Log.e("选中的文本："+mSelectionInfo.mSelectionContent);
    }

    fun setSelectListener(selectListener: OnSelectListener) {
        mSelectListener = selectListener
    }

    fun destroy() {
        resetSelectionInfo()
        hideSelectView()
    }

    /**
     * Operate windows : copy, select all
     */
    private inner class OperateWindow(context: Context?) {
        private val mWindow: PopupWindow
        private val mTempCoors = IntArray(2)

        private val mWidth: Int
        private val mHeight: Int

        init {
            val contentView = LayoutInflater.from(context).inflate(R.layout.layout_operate_windows, null)
            contentView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            mWidth = contentView.getMeasuredWidth()
            mHeight = contentView.getMeasuredHeight()
            mWindow =
                PopupWindow(contentView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false)
            mWindow.isClippingEnabled = false
            contentView.findViewById<View?>(R.id.txt_search).click {
                try {
                    val encode = URLEncoder.encode(mLocation.getTxt(mSelectionInfo.start, mSelectionInfo.end), "utf-8")
                    val uri = ("https://www.bing.com/search?q=$encode").toUri()
                    mView.context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    this@SelectableTextHelper.resetSelectionInfo()
                    this@SelectableTextHelper.hideSelectView()
                    mSelectListener!!.onTextSelectedChange(mSelectionInfo)
                } catch (e: Exception) {
                    Log.e("搜索出错", e)
                }
            }
            contentView.findViewById<View?>(R.id.txt_dict).click {
                try {
                    val encode = URLEncoder.encode(mLocation.getTxt(mSelectionInfo.start, mSelectionInfo.end), "utf-8")
                    val uri = ("https://hanyu.baidu.com/s?wd=$encode").toUri()
                    mView.context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    this@SelectableTextHelper.resetSelectionInfo()
                    this@SelectableTextHelper.hideSelectView()
                    mSelectListener!!.onTextSelectedChange(mSelectionInfo)
                } catch (e: Exception) {
                    Log.e("字典搜索出错", e)
                }
            }
        }

        fun show() {
            mView.getLocationInWindow(mTempCoors)

            val startX = mLocation.getHorizontalLeft(mSelectionInfo.start) + mTempCoors[0] + mView.getPaddingLeft()
            val posY = mLocation.getLineTop(mLocation.getLineForOffset(mSelectionInfo.start)) + mTempCoors[1] - mHeight - 16

            mWindow.setElevation(8f)

            val arrowMargin = mWidth * 0.15f
            var posX = startX - arrowMargin
            val screenWidth = TextLayoutUtil.getScreenWidth(mContext)
            if (posX + mWidth > screenWidth) {
                posX = (screenWidth - mWidth).toFloat()
            } else if (posX < 0) {
                posX = 0f
            }

            val contentView = mWindow.getContentView()
            val arrow = contentView.findViewById<View>(R.id.arrow)
            val params = arrow.getLayoutParams() as LinearLayout.LayoutParams

            params.gravity = Gravity.NO_GRAVITY

            var leftMargin = startX - posX
            if (leftMargin < arrowMargin) {
                leftMargin = arrowMargin
            } else if (leftMargin > mWidth - arrowMargin) {
                leftMargin = mWidth - arrowMargin
            }

            params.leftMargin = Math.round(leftMargin)

            arrow.setLayoutParams(params)

            mWindow.showAtLocation(mView, Gravity.NO_GRAVITY, Math.round(posX), Math.round(max(posY, 16f)))
        }

        fun dismiss() {
            mWindow.dismiss()
        }

        val isShowing: Boolean
            get() = mWindow.isShowing()
    }

    private inner class CursorHandle(var isLeft: Boolean) : View(mContext) {
        private val mPopupWindow: PopupWindow
        private val mPaint: Paint

        private val mCircleRadius = mCursorHandleSize / 2f
        private val mWidth = mCircleRadius * 2
        private val mPadding = 25f

        override fun onDraw(canvas: Canvas) {
            canvas.drawCircle(mCircleRadius + mPadding, mCircleRadius, mCircleRadius, mPaint)
            if (isLeft) {
                canvas.drawRect(mCircleRadius + mPadding, 0f, mCircleRadius * 2 + mPadding, mCircleRadius, mPaint)
            } else {
                canvas.drawRect(mPadding, 0f, mCircleRadius + mPadding, mCircleRadius, mPaint)
            }
        }

        private var mAdjustX = 0f
        private var mAdjustY = 0f

        private var mBeforeDragStart = 0
        private var mBeforeDragEnd = 0

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.getAction()) {
                MotionEvent.ACTION_DOWN -> {
                    mBeforeDragStart = mSelectionInfo.start
                    mBeforeDragEnd = mSelectionInfo.end
                    mAdjustX = event.getX()
                    mAdjustY = event.getY()
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mOperateWindow!!.show()
                MotionEvent.ACTION_MOVE -> {
                    mOperateWindow!!.dismiss()
                    val rawX = event.getRawX()
                    val rawY = event.getRawY()
                    mView.getLocationInWindow(mTempCoors)
                    //                    Log.e("rawX:" + rawX + " rawY:" + rawY + " mAdjustX:" + mAdjustX + " mAdjustY:" + mAdjustY + " TempCoors0:" + mTempCoors[0] + " TempCoors1:" + mTempCoors[1]);
                    if (isLeft) {
                        update(
                            rawX - mAdjustX + mPadding + mWidth - mTempCoors[0] - mView.getPaddingLeft(),
                            rawY - mAdjustY + mPadding / 4 - mTempCoors[1] - mView.getPaddingTop()
                        )
                    } else {
                        update(
                            rawX - mAdjustX + mPadding - mTempCoors[0] - mView.getPaddingLeft(),
                            rawY - mAdjustY + mPadding / 4 - mTempCoors[1] - mView.getPaddingTop()
                        )
                    }
                }
            }
            return true
        }

        fun changeDirection() {
            isLeft = !isLeft
            invalidate()
        }

        fun dismiss() {
            mPopupWindow.dismiss()
        }

        private val mTempCoors = IntArray(2)

        init {
            mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            mPaint.setColor(mCursorHandleColor)

            mPopupWindow = PopupWindow(this)
            mPopupWindow.setClippingEnabled(false)
            mPopupWindow.setWidth(Math.round(mWidth + mPadding * 2))
            mPopupWindow.setHeight(Math.round(mCircleRadius * 2 + mPadding / 2))
            invalidate()
        }

        fun update(x: Float, y: Float) {
            val oldOffset: Int
            if (isLeft) {
                oldOffset = mSelectionInfo.start
            } else {
                oldOffset = mSelectionInfo.end
            }

            //            Log.e("Handle：x:" + x + " y:" + y);
            val offset = mLocation.getHysteresisOffset(x, y, oldOffset, isLeft)

            if (offset != oldOffset) {
                resetSelectionInfo()
                if (isLeft) {
                    if (offset > mBeforeDragEnd) {
                        val handle = getCursorHandle(false)
                        changeDirection()
                        handle.changeDirection()
                        mBeforeDragStart = mBeforeDragEnd
                        selectText(mBeforeDragEnd, offset)
                        handle.updateCursorHandle()
                    } else {
                        selectText(offset, -1)
                    }
                    updateCursorHandle()
                } else {
                    if (offset < mBeforeDragStart) {
                        val handle = getCursorHandle(true)
                        handle.changeDirection()
                        changeDirection()
                        mBeforeDragEnd = mBeforeDragStart
                        selectText(offset, mBeforeDragStart)
                        handle.updateCursorHandle()
                    } else {
                        selectText(mBeforeDragStart, offset)
                    }
                    updateCursorHandle()
                }
            }
        }

        fun updateCursorHandle() {
            mView.getLocationInWindow(mTempCoors)
            if (isLeft) {
                mPopupWindow.update(
                    Math.round(mLocation.getHorizontalLeft(mSelectionInfo.start) - mWidth - mPadding + mTempCoors[0] + mView.getPaddingLeft()),
                    Math.round(mLocation.getLineBottom(mLocation.getLineForOffset(mSelectionInfo.start)) - mPadding / 4 + mTempCoors[1] + mView.getPaddingTop()),
                    -1, -1
                )
            } else {
                mPopupWindow.update(
                    Math.round(mLocation.getHorizontalRight(mSelectionInfo.end) - mPadding + mTempCoors[0] + mView.getPaddingLeft()),
                    Math.round(mLocation.getLineBottom(mLocation.getLineForOffset(mSelectionInfo.end)) - mPadding / 4 + mTempCoors[1] + mView.getPaddingTop()),
                    -1, -1
                )
            }
        }

        fun show(x: Float, y: Float) {
            mView.getLocationInWindow(mTempCoors)
            if (isLeft) {
                mPopupWindow.showAtLocation(
                    mView,
                    Gravity.NO_GRAVITY,
                    (x - mWidth - mPadding + mTempCoors[0] + mView.getPaddingLeft()).roundToInt(),
                    (y - mPadding / 4 + mTempCoors[1] + mView.paddingTop).roundToInt()
                )
            } else {
                mPopupWindow.showAtLocation(
                    mView,
                    Gravity.NO_GRAVITY,
                    (x - mPadding + mTempCoors[0] + mView.getPaddingLeft()).roundToInt(),
                    (y - mPadding / 4 + mTempCoors[1] + mView.paddingTop).roundToInt()
                )
            }
        }
    }

    private fun getCursorHandle(isLeft: Boolean): CursorHandle {
        if (mStartHandle?.isLeft == isLeft) {
            return mStartHandle!!
        } else {
            return mEndHandle!!
        }
    }

    class Builder(val mView: View, val mLocation: TxtLocation) {
        var mCursorHandleColor = Color.parseColor("#00CF7A")
        var mSelectedColor = Color.parseColor("#3D00CF7A")
        var mCursorHandleSizeInDp = 20f

        fun setCursorHandleColor(@ColorInt cursorHandleColor: Int): Builder {
            mCursorHandleColor = cursorHandleColor
            return this
        }

        fun setCursorHandleSizeInDp(cursorHandleSizeInDp: Float): Builder {
            mCursorHandleSizeInDp = cursorHandleSizeInDp
            return this
        }

        fun setSelectedColor(@ColorInt selectedBgColor: Int): Builder {
            mSelectedColor = selectedBgColor
            return this
        }

        fun build(): SelectableTextHelper {
            return SelectableTextHelper(this)
        }
    }

    companion object {
        private const val DEFAULT_SHOW_DURATION = 100
    }
}


