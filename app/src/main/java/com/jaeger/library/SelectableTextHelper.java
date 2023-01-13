package com.jaeger.library;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import androidx.annotation.ColorInt;

import com.sjianjun.reader.R;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import kotlin.text.StringsKt;
import sjj.alog.Log;

/**
 * Created by Jaeger on 16/8/30.
 * <p>
 * Email: chjie.jaeger@gmail.com
 * GitHub: https://github.com/laobie
 */
public class SelectableTextHelper {

    private static final int DEFAULT_SHOW_DURATION = 100;

    private CursorHandle mStartHandle;
    private CursorHandle mEndHandle;
    private OperateWindow mOperateWindow;
    public SelectionInfo mSelectionInfo = new SelectionInfo();
    private OnSelectListener mSelectListener;

    private Context mContext;
    private View mView;
    private TxtLocation mLocation;

    private int mSelectedColor;
    private int mCursorHandleColor;
    private int mCursorHandleSize;
    private boolean isHideWhenScroll;

    private ViewTreeObserver.OnPreDrawListener mOnPreDrawListener;
    ViewTreeObserver.OnScrollChangedListener mOnScrollChangedListener;

    public SelectableTextHelper(Builder builder) {
        mView = builder.mView;
        mLocation = builder.mLocation;
        mContext = mView.getContext();
        mSelectedColor = builder.mSelectedColor;
        mCursorHandleColor = builder.mCursorHandleColor;
        mCursorHandleSize = TextLayoutUtil.dp2px(mContext, builder.mCursorHandleSizeInDp);
        init();
    }

    private void init() {

//        mView.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                resetSelectionInfo();
//                hideSelectView();
//            }
//        });
        mView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {

            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                destroy();
            }
        });

        mOnPreDrawListener = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (isHideWhenScroll) {
                    isHideWhenScroll = false;
                    postShowSelectView(DEFAULT_SHOW_DURATION);
                }
                return true;
            }
        };
        mView.getViewTreeObserver().addOnPreDrawListener(mOnPreDrawListener);

        mOnScrollChangedListener = new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                if (!isHideWhenScroll && !mSelectionInfo.select) {
                    isHideWhenScroll = true;
                    if (mOperateWindow != null) {
                        mOperateWindow.dismiss();
                    }
                    if (mStartHandle != null) {
                        mStartHandle.dismiss();
                    }
                    if (mEndHandle != null) {
                        mEndHandle.dismiss();
                    }
                }
            }
        };
        mView.getViewTreeObserver().addOnScrollChangedListener(mOnScrollChangedListener);

        mOperateWindow = new OperateWindow(mContext);
    }

    private void postShowSelectView(int duration) {
        mView.removeCallbacks(mShowSelectViewRunnable);
        if (duration <= 0) {
            mShowSelectViewRunnable.run();
        } else {
            mView.postDelayed(mShowSelectViewRunnable, duration);
        }
    }

    private final Runnable mShowSelectViewRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mSelectionInfo.select) return;
            if (mOperateWindow != null) {
                mOperateWindow.show();
            }
            if (mStartHandle != null) {
                showCursorHandle(mStartHandle);
            }
            if (mEndHandle != null) {
                showCursorHandle(mEndHandle);
            }
        }
    };

    public void hideSelectView() {
        mSelectionInfo.select = false;
        if (mStartHandle != null) {
            mStartHandle.dismiss();
        }
        if (mEndHandle != null) {
            mEndHandle.dismiss();
        }
        if (mOperateWindow != null) {
            mOperateWindow.dismiss();
        }
    }

    public void resetSelectionInfo() {
        mSelectionInfo.select = false;
    }

    public void showSelectView(int x, int y) {
        hideSelectView();
        resetSelectionInfo();
        if (mStartHandle == null) mStartHandle = new CursorHandle(true);
        if (mEndHandle == null) mEndHandle = new CursorHandle(false);

        int startOffset = mLocation.getOffset(x, y);
        if (startOffset < 0) {
            return;
        }
        int endOffset = startOffset;
        if (StringsKt.isBlank(mLocation.getTxt(startOffset, endOffset))) {
            return;
        }

        selectText(startOffset, endOffset);
        showCursorHandle(mStartHandle);
        showCursorHandle(mEndHandle);
        mOperateWindow.show();
    }

    private void showCursorHandle(CursorHandle cursorHandle) {
        int offset = cursorHandle.isLeft ? mSelectionInfo.start : mSelectionInfo.end;
        int line = mLocation.getLineForOffset(offset);
        int x = cursorHandle.isLeft ? mLocation.getHorizontalLeft(offset) : mLocation.getHorizontalRight(offset);
        cursorHandle.show(x, mLocation.getLineBottom(line));
    }

    private void selectText(int startPos, int endPos) {
        int oldS = mSelectionInfo.start;
        int oldE = mSelectionInfo.end;
        if (startPos != -1) {
            mSelectionInfo.start = startPos;
        }
        if (endPos != -1) {
            mSelectionInfo.end = endPos;
        }
        if (mSelectionInfo.start > mSelectionInfo.end) {
            int temp = mSelectionInfo.start;
            mSelectionInfo.start = mSelectionInfo.end;
            mSelectionInfo.end = temp;
        }
//        Log.e(mSelectionInfo + mLocation.getTxt(mSelectionInfo.start, mSelectionInfo.end));
        if (oldS != mSelectionInfo.start || oldE != mSelectionInfo.end || !mSelectionInfo.select) {
            mSelectionInfo.select = true;
            mSelectListener.onTextSelectedChange(mSelectionInfo);
        }
//        int[] offset = checkOffset(mLocation, mSelectionInfo.start, mSelectionInfo.end);
//        mSelectionInfo.start = offset[0];
//        mSelectionInfo.end = offset[1];
//
//        mSelectionInfo.mSelectionContent = mLocation.getText().subSequence(mSelectionInfo.start, mSelectionInfo.end).toString();
//        Log.e("选中的文本："+mSelectionInfo.mSelectionContent);
    }

    public void setSelectListener(OnSelectListener selectListener) {
        mSelectListener = selectListener;
    }

    public void destroy() {
        resetSelectionInfo();
        hideSelectView();
    }

    /**
     * Operate windows : copy, select all
     */
    private class OperateWindow {

        private PopupWindow mWindow;
        private int[] mTempCoors = new int[2];

        private int mWidth;
        private int mHeight;

        public OperateWindow(final Context context) {
            View contentView = LayoutInflater.from(context).inflate(R.layout.layout_operate_windows, null);
            contentView.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            mWidth = contentView.getMeasuredWidth();
            mHeight = contentView.getMeasuredHeight();
            mWindow =
                    new PopupWindow(contentView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);
            mWindow.setClippingEnabled(false);
            contentView.findViewById(R.id.txt_search).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        String encode = URLEncoder.encode(mLocation.getTxt(mSelectionInfo.start, mSelectionInfo.end), "utf-8");
                        Uri uri = Uri.parse("https://www.bing.com/search?q=" + encode);
                        mView.getContext().startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        SelectableTextHelper.this.resetSelectionInfo();
                        SelectableTextHelper.this.hideSelectView();
                        mSelectListener.onTextSelectedChange(mSelectionInfo);
                    } catch (Exception e) {
                        Log.e("搜索出错", e);
                    }
                }
            });
            contentView.findViewById(R.id.txt_dict).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        String encode = URLEncoder.encode(mLocation.getTxt(mSelectionInfo.start, mSelectionInfo.end), "utf-8");
                        Uri uri = Uri.parse("https://hanyu.baidu.com/s?wd=" + encode);
                        mView.getContext().startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        SelectableTextHelper.this.resetSelectionInfo();
                        SelectableTextHelper.this.hideSelectView();
                        mSelectListener.onTextSelectedChange(mSelectionInfo);
                    } catch (Exception e) {
                        Log.e("字典搜索出错", e);
                    }
                }
            });
        }

        public void show() {
            mView.getLocationInWindow(mTempCoors);

//            if (isLeft) {
//                mPopupWindow.update((int) mLocation.getHorizontalLeft(mSelectionInfo.start) - mWidth - mPadding + mTempCoors[0] + mView.getPaddingLeft(),
//                        mLocation.getLineBottom(mLocation.getLineForOffset(mSelectionInfo.start)) - mPadding / 4 + mTempCoors[1] + mView.getPaddingTop(),
//                        -1, -1);
//            } else {
//                int line = mLocation.getLineForOffset(mSelectionInfo.end);
//                int x = mLocation.getHorizontalLeft(mSelectionInfo.end);
//                if (mLocation.getLineStartOffset(line) == mSelectionInfo.end) {
//                    line -= 1;
//                    x = mLocation.getLineEnd(line);
//                }
//                mPopupWindow.update(x - mPadding + mTempCoors[0] + mView.getPaddingLeft(),
//                        mLocation.getLineBottom(line) - mPadding / 4 + mTempCoors[1] + mView.getPaddingTop(),
//                        -1, -1);
//            }

            int startX = mLocation.getHorizontalLeft(mSelectionInfo.start) + mTempCoors[0] + mView.getPaddingLeft();
            int posY = mLocation.getLineTop(mLocation.getLineForOffset(mSelectionInfo.start)) + mTempCoors[1] - mHeight - 16;

            mWindow.setElevation(8f);

            float arrowMargin = mWidth * 0.15f;
            float posX = startX - arrowMargin;
            int screenWidth = TextLayoutUtil.getScreenWidth(mContext);
            if (posX + mWidth > screenWidth) {
                posX = screenWidth - mWidth;
            } else if (posX < 0) {
                posX = 0;
            }

            View contentView = mWindow.getContentView();
            View arrow = contentView.findViewById(R.id.arrow);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) arrow.getLayoutParams();

            params.gravity = Gravity.NO_GRAVITY;

            float leftMargin = startX - posX;
            if (leftMargin < arrowMargin) {
                leftMargin = arrowMargin;
            } else if (leftMargin > mWidth - arrowMargin) {
                leftMargin = mWidth - arrowMargin;
            }

            params.leftMargin = (int) (leftMargin + 0.5);

            arrow.setLayoutParams(params);

            mWindow.showAtLocation(mView, Gravity.NO_GRAVITY, (int) (posX + 0.5), Math.max(posY, 16));
        }

        public void dismiss() {
            mWindow.dismiss();
        }

        public boolean isShowing() {
            return mWindow.isShowing();
        }
    }

    private class CursorHandle extends View {

        private PopupWindow mPopupWindow;
        private Paint mPaint;

        private int mCircleRadius = mCursorHandleSize / 2;
        private int mWidth = mCircleRadius * 2;
        private int mHeight = mCircleRadius * 2;
        private int mPadding = 25;
        private boolean isLeft;

        public CursorHandle(boolean isLeft) {
            super(mContext);
            this.isLeft = isLeft;
            mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mPaint.setColor(mCursorHandleColor);

            mPopupWindow = new PopupWindow(this);
            mPopupWindow.setClippingEnabled(false);
            mPopupWindow.setWidth(mWidth + mPadding * 2);
            mPopupWindow.setHeight(mHeight + mPadding / 2);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawCircle(mCircleRadius + mPadding, mCircleRadius, mCircleRadius, mPaint);
            if (isLeft) {
                canvas.drawRect(mCircleRadius + mPadding, 0, mCircleRadius * 2 + mPadding, mCircleRadius, mPaint);
            } else {
                canvas.drawRect(mPadding, 0, mCircleRadius + mPadding, mCircleRadius, mPaint);
            }
        }

        private int mAdjustX;
        private int mAdjustY;

        private int mBeforeDragStart;
        private int mBeforeDragEnd;

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mBeforeDragStart = mSelectionInfo.start;
                    mBeforeDragEnd = mSelectionInfo.end;
                    mAdjustX = (int) event.getX();
                    mAdjustY = (int) event.getY();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mOperateWindow.show();
                    break;
                case MotionEvent.ACTION_MOVE:
                    mOperateWindow.dismiss();
                    int rawX = (int) event.getRawX();
                    int rawY = (int) event.getRawY();
                    mView.getLocationInWindow(mTempCoors);
//                    Log.e("rawX:" + rawX + " rawY:" + rawY + " mAdjustX:" + mAdjustX + " mAdjustY:" + mAdjustY + " TempCoors0:" + mTempCoors[0] + " TempCoors1:" + mTempCoors[1]);
                    if (isLeft) {
                        update(rawX - mAdjustX + mPadding + mWidth - mTempCoors[0] - mView.getPaddingLeft(),
                                rawY - mAdjustY + mPadding / 4 - mTempCoors[1] - mView.getPaddingTop());
                    } else {
                        update(rawX - mAdjustX + mPadding - mTempCoors[0] - mView.getPaddingLeft(),
                                rawY - mAdjustY + mPadding / 4 - mTempCoors[1] - mView.getPaddingTop());
                    }

                    break;
            }
            return true;
        }

        private void changeDirection() {
            isLeft = !isLeft;
            invalidate();
        }

        public void dismiss() {
            mPopupWindow.dismiss();
        }

        private int[] mTempCoors = new int[2];

        public void update(int x, int y) {
            int oldOffset;
            if (isLeft) {
                oldOffset = mSelectionInfo.start;
            } else {
                oldOffset = mSelectionInfo.end;
            }

//            Log.e("Handle：x:" + x + " y:" + y);
            int offset = mLocation.getHysteresisOffset(x, y, oldOffset, isLeft);

            if (offset != oldOffset) {
                resetSelectionInfo();
                if (isLeft) {
                    if (offset > mBeforeDragEnd) {
                        CursorHandle handle = getCursorHandle(false);
                        changeDirection();
                        handle.changeDirection();
                        mBeforeDragStart = mBeforeDragEnd;
                        selectText(mBeforeDragEnd, offset);
                        handle.updateCursorHandle();
                    } else {
                        selectText(offset, -1);
                    }
                    updateCursorHandle();
                } else {
                    if (offset < mBeforeDragStart) {
                        CursorHandle handle = getCursorHandle(true);
                        handle.changeDirection();
                        changeDirection();
                        mBeforeDragEnd = mBeforeDragStart;
                        selectText(offset, mBeforeDragStart);
                        handle.updateCursorHandle();
                    } else {
                        selectText(mBeforeDragStart, offset);
                    }
                    updateCursorHandle();
                }
            }
        }

        private void updateCursorHandle() {
            mView.getLocationInWindow(mTempCoors);
            if (isLeft) {
                mPopupWindow.update((int) mLocation.getHorizontalLeft(mSelectionInfo.start) - mWidth - mPadding + mTempCoors[0] + mView.getPaddingLeft(),
                        mLocation.getLineBottom(mLocation.getLineForOffset(mSelectionInfo.start)) - mPadding / 4 + mTempCoors[1] + mView.getPaddingTop(),
                        -1, -1);
            } else {
                int line = mLocation.getLineForOffset(mSelectionInfo.end);
                int x = mLocation.getHorizontalRight(mSelectionInfo.end);
                mPopupWindow.update(x - mPadding + mTempCoors[0] + mView.getPaddingLeft(),
                        mLocation.getLineBottom(line) - mPadding / 4 + mTempCoors[1] + mView.getPaddingTop(),
                        -1, -1);
            }
        }

        public void show(int x, int y) {
            mView.getLocationInWindow(mTempCoors);
            if (isLeft) {
                mPopupWindow.showAtLocation(mView,
                        Gravity.NO_GRAVITY,
                        x - mWidth - mPadding + mTempCoors[0] + mView.getPaddingLeft(),
                        y - mPadding / 4 + mTempCoors[1] + mView.getPaddingTop());
//                Log.e("show x:" + x + " y:" + y +
//                        " showAtLocation x:" + (x - mWidth - mPadding + mTempCoors[0] + mView.getPaddingLeft()) +
//                        " y:" + (y - mPadding / 4 + mTempCoors[1] + mView.getPaddingTop()) + " isLeft:" + isLeft);
            } else {
                mPopupWindow.showAtLocation(mView,
                        Gravity.NO_GRAVITY,
                        x - mPadding + mTempCoors[0] + mView.getPaddingLeft(),
                        y - mPadding / 4 + mTempCoors[1] + mView.getPaddingTop());
//                Log.e("show x:" + x + " y:" + y +
//                        " showAtLocation x:" + (x - mPadding + mTempCoors[0] + mView.getPaddingLeft()) +
//                        " y:" + (y - mPadding / 4 + mTempCoors[1] + mView.getPaddingTop()) + " isLeft:" + isLeft);
            }
        }

//        public int getExtraX() {
////            Log.e("getExtraX:" + (mTempCoors[0] - mPadding + mView.getPaddingLeft()));
//            return mTempCoors[0] - mPadding + mView.getPaddingLeft();
//        }
//
//        public int getExtraY() {
//            Log.e("getExtraY:" + mTempCoors[1] + "   " + mView.getPaddingTop());
//            return mTempCoors[1] + mView.getPaddingTop();
//        }
    }

    private CursorHandle getCursorHandle(boolean isLeft) {
        if (mStartHandle.isLeft == isLeft) {
            return mStartHandle;
        } else {
            return mEndHandle;
        }
    }

    public static class Builder {
        private View mView;
        private TxtLocation mLocation;
        private int mCursorHandleColor = Color.parseColor("#00CF7A");
        private int mSelectedColor = Color.parseColor("#3D00CF7A");
        private float mCursorHandleSizeInDp = 20;

        public Builder(View view, TxtLocation location) {
            mView = view;
            mLocation = location;
        }

        public Builder setCursorHandleColor(@ColorInt int cursorHandleColor) {
            mCursorHandleColor = cursorHandleColor;
            return this;
        }

        public Builder setCursorHandleSizeInDp(float cursorHandleSizeInDp) {
            mCursorHandleSizeInDp = cursorHandleSizeInDp;
            return this;
        }

        public Builder setSelectedColor(@ColorInt int selectedBgColor) {
            mSelectedColor = selectedBgColor;
            return this;
        }

        public SelectableTextHelper build() {
            return new SelectableTextHelper(this);
        }
    }
}


