package com.jaeger.library;

import android.graphics.PointF;

public interface OnSelectListener {

    int HANDLE_DIRECTION_LEFT = 0;
    int HANDLE_DIRECTION_RIGHT = 1;
    int HANDLE_DIRECTION_TOP = 2;
    int HANDLE_DIRECTION_BOTTOM = 3;

    /**
     * 获取行号
     */
    int getLine(float x, float y);
    /**
     * 获取行号
     */
    int getLineForOffset(int offset);
    /**
     * 获取字符索引
     */
    int getOffset(float x,float y);
    int getHysteresisOffset(float x,float y,int oldOffset,boolean isLeft);

    PointF getHandlePosition(int offset, boolean isStartHandle);

    int getHandleDirection(boolean isStartHandle);

    PointF getOperateWindowAnchor(int start, int end);

    /**
     *
     * @param start 开始索引
     * @param end 结束索引。包含索引字符
     */
    String getTxt(int start,int end);

    void onTextSelectedChange(SelectionInfo info);
}
