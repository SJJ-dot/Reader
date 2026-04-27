package com.jaeger.library

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.annotation.ColorInt
import androidx.core.net.toUri
import com.sjianjun.reader.R
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.click
import sjj.alog.Log
import java.net.URLEncoder
import kotlin.math.max
import kotlin.math.roundToInt

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
    private val mSelectListener: OnSelectListener = builder.onSelectListener

    private val mContext: Context = builder.mView.context
    private val mView: View = builder.mView
    private val mCursorHandleColor: Int = builder.mCursorHandleColor
    private val mCursorHandleSize: Int = TextLayoutUtil.dp2px(mContext, builder.mCursorHandleSizeInDp)

    init {
        mView.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
            }

            override fun onViewDetachedFromWindow(v: View) {
                destroy()
            }
        })
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

    fun showSelectView(x: Float, y: Float) {
        hideSelectView()
        resetSelectionInfo()
        if (mStartHandle == null) mStartHandle = CursorHandle(true)
        if (mEndHandle == null) mEndHandle = CursorHandle(false)

        val startOffset = mSelectListener.getOffset(x, y)
        if (startOffset < 0) {
            return
        }
        if (mSelectListener.getTxt(startOffset, startOffset).isBlank()) {
            return
        }
        selectText(startOffset, startOffset)
        showCursorHandle(mStartHandle!!)
        showCursorHandle(mEndHandle!!)
        mOperateWindow!!.show()
    }

    private fun showCursorHandle(cursorHandle: CursorHandle) {
        val isStartHandle = cursorHandle.isLeft
        val offset = if (isStartHandle) mSelectionInfo.start else mSelectionInfo.end
        val handlePos = mSelectListener.getHandlePosition(offset, isStartHandle)
        cursorHandle.show(handlePos.x, handlePos.y)
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
        //        Log.e(mSelectionInfo + mSelectListener.getTxt(mSelectionInfo.start, mSelectionInfo.end));
        if (oldS != mSelectionInfo.start || oldE != mSelectionInfo.end || !mSelectionInfo.select) {
            mSelectionInfo.select = true
            mSelectListener.onTextSelectedChange(mSelectionInfo)
        }
        //        int[] offset = checkOffset(mSelectListener, mSelectionInfo.start, mSelectionInfo.end);
//        mSelectionInfo.start = offset[0];
//        mSelectionInfo.end = offset[1];
//
//        mSelectionInfo.mSelectionContent = mSelectListener.getText().subSequence(mSelectionInfo.start, mSelectionInfo.end).toString();
//        Log.e("选中的文本："+mSelectionInfo.mSelectionContent);
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
            contentView.findViewById<View>(R.id.txt_search).click {
                try {
                    val encode = URLEncoder.encode(mSelectListener.getTxt(mSelectionInfo.start, mSelectionInfo.end), "utf-8")
                    val uri = ("https://www.bing.com/search?q=$encode").toUri()
                    mView.context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    this@SelectableTextHelper.resetSelectionInfo()
                    this@SelectableTextHelper.hideSelectView()
                    mSelectListener.onTextSelectedChange(mSelectionInfo)
                } catch (e: Exception) {
                    Log.e("搜索出错", e)
                }
            }
            contentView.findViewById<View>(R.id.txt_dict).click {
                try {
                    val encode = URLEncoder.encode(mSelectListener.getTxt(mSelectionInfo.start, mSelectionInfo.end), "utf-8")
                    val uri = ("https://hanyu.baidu.com/s?wd=$encode").toUri()
                    mView.context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    this@SelectableTextHelper.resetSelectionInfo()
                    this@SelectableTextHelper.hideSelectView()
                    mSelectListener.onTextSelectedChange(mSelectionInfo)
                } catch (e: Exception) {
                    Log.e("字典搜索出错", e)
                }
            }
            contentView.findViewById<View>(R.id.txt_copy).click {
                try {
                    val str = mSelectListener.getTxt(mSelectionInfo.start, mSelectionInfo.end)
                    // 获取系统剪贴板服务
                    val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clipData = android.content.ClipData.newPlainText("text", str)
                    clipboard?.setPrimaryClip(clipData)
                    toast("复制成功：${str}")
                    this@SelectableTextHelper.resetSelectionInfo()
                    this@SelectableTextHelper.hideSelectView()
                    mSelectListener.onTextSelectedChange(mSelectionInfo)
                } catch (e: Exception) {
                    Log.e("复制出错", e)
                }
            }
        }

        fun show() {
            mView.getLocationInWindow(mTempCoors)
            val anchor = mSelectListener.getOperateWindowAnchor(mSelectionInfo.start, mSelectionInfo.end)

            val startX = anchor.x + mTempCoors[0] + mView.paddingLeft
            val posY = anchor.y + mTempCoors[1] + mView.paddingTop - mHeight - 16

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
        private val mHandleWidth = mCircleRadius * 2
        private val mStemThickness = max(mCircleRadius, 6f)
        private val mPadding = 25f

        private fun getHandleDirection(): Int = mSelectListener.getHandleDirection(isLeft)

        private fun getPopupWidth(direction: Int = getHandleDirection()): Int {
            val width = when (direction) {
                OnSelectListener.HANDLE_DIRECTION_LEFT,
                OnSelectListener.HANDLE_DIRECTION_RIGHT -> mHandleWidth + mPadding * 2

                else -> mHandleWidth + mPadding / 2f
            }
            return width.roundToInt()
        }

        private fun getPopupHeight(direction: Int = getHandleDirection()): Int {
            val height = when (direction) {
                OnSelectListener.HANDLE_DIRECTION_TOP,
                OnSelectListener.HANDLE_DIRECTION_BOTTOM -> mHandleWidth + mPadding * 2

                else -> mHandleWidth + mPadding / 2f
            }
            return height.roundToInt()
        }

        private fun getAnchorOffset(direction: Int = getHandleDirection()): PointF {
            val width = getPopupWidth(direction).toFloat()
            val height = getPopupHeight(direction).toFloat()
            return when (direction) {
                OnSelectListener.HANDLE_DIRECTION_LEFT -> PointF(width - mPadding, mPadding / 4f)
                OnSelectListener.HANDLE_DIRECTION_RIGHT -> PointF(mPadding, mPadding / 4f)
                OnSelectListener.HANDLE_DIRECTION_TOP -> PointF(width / 2f, height - mPadding)
                else -> PointF(width / 2f, mPadding)
            }
        }

        private fun updateWindowSize() {
            val direction = getHandleDirection()
            mPopupWindow.width = getPopupWidth(direction)
            mPopupWindow.height = getPopupHeight(direction)
            requestLayout()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(getPopupWidth(), getPopupHeight())
        }

        override fun onDraw(canvas: Canvas) {
            val direction = getHandleDirection()
            val width = getPopupWidth(direction).toFloat()
            val height = getPopupHeight(direction).toFloat()
            when (direction) {
                OnSelectListener.HANDLE_DIRECTION_LEFT -> {
                    val cy = mCircleRadius
                    val cx = mPadding + mCircleRadius
                    canvas.drawRect(cx, 0f, mPadding + mHandleWidth, mCircleRadius, mPaint)
                    canvas.drawCircle(cx, cy, mCircleRadius, mPaint)
                }

                OnSelectListener.HANDLE_DIRECTION_RIGHT -> {
                    val cy = mCircleRadius
                    val cx = width - mPadding - mCircleRadius
                    canvas.drawRect(mPadding, 0f, mPadding + mCircleRadius, mCircleRadius, mPaint)
                    canvas.drawCircle(cx, cy, mCircleRadius, mPaint)
                }

                OnSelectListener.HANDLE_DIRECTION_TOP -> {
                    val cx = width / 2f
                    val cy = mPadding + mCircleRadius
                    canvas.drawRect(cx - mStemThickness / 2f, cy, cx + mStemThickness / 2f, height - mPadding, mPaint)
                    canvas.drawCircle(cx, cy, mCircleRadius, mPaint)
                }

                else -> {
                    val cx = width / 2f
                    val cy = height - mPadding - mCircleRadius
                    canvas.drawRect(cx - mStemThickness / 2f, mPadding, cx + mStemThickness / 2f, cy, mPaint)
                    canvas.drawCircle(cx, cy, mCircleRadius, mPaint)
                }
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

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    performClick()
                    mOperateWindow!!.show()
                }
                MotionEvent.ACTION_MOVE -> {
                    mOperateWindow!!.dismiss()
                    val rawX = event.getRawX()
                    val rawY = event.getRawY()
                    mView.getLocationInWindow(mTempCoors)
                    val anchorOffset = getAnchorOffset()
                    update(
                        rawX - mAdjustX + anchorOffset.x - mTempCoors[0] - mView.paddingLeft,
                        rawY - mAdjustY + anchorOffset.y - mTempCoors[1] - mView.paddingTop
                    )
                }
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        fun changeDirection() {
            isLeft = !isLeft
            updateWindowSize()
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
            updateWindowSize()
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
            val offset = mSelectListener.getHysteresisOffset(x, y, oldOffset, isLeft)

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
            updateWindowSize()
            val isStartHandle = isLeft
            val offset = if (isStartHandle) mSelectionInfo.start else mSelectionInfo.end
            val handlePos = mSelectListener.getHandlePosition(offset, isStartHandle)
            val anchorOffset = getAnchorOffset()
            mPopupWindow.update(
                (handlePos.x - anchorOffset.x + mTempCoors[0] + mView.paddingLeft).roundToInt(),
                (handlePos.y - anchorOffset.y + mTempCoors[1] + mView.paddingTop).roundToInt(),
                -1,
                -1
            )
        }

        fun show(x: Float, y: Float) {
            mView.getLocationInWindow(mTempCoors)
            updateWindowSize()
            val anchorOffset = getAnchorOffset()
            mPopupWindow.showAtLocation(
                mView,
                Gravity.NO_GRAVITY,
                (x - anchorOffset.x + mTempCoors[0] + mView.paddingLeft).roundToInt(),
                (y - anchorOffset.y + mTempCoors[1] + mView.paddingTop).roundToInt()
            )
        }
    }

    private fun getCursorHandle(isLeft: Boolean): CursorHandle {
        if (mStartHandle?.isLeft == isLeft) {
            return mStartHandle!!
        } else {
            return mEndHandle!!
        }
    }

    class Builder(val mView: View, val onSelectListener: OnSelectListener) {
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


