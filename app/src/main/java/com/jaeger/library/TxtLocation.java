package com.jaeger.library;

public interface TxtLocation {

    /**
     * 获取行号
     */
    int getLine(float y);
    /**
     * 获取行号
     */
    int getLineForOffset(int offset);
    float getLineStart(int line);
    int getLineStartOffset(int line);
    float getLineEnd(int line);

    /**
     * 获取字符索引
     */
    int getOffset(float x,float y);
    int getHysteresisOffset(float x,float y,int oldOffset,boolean isLeft);



    float getHorizontalRight(int offset);
    float getHorizontalLeft(int offset);

    float getLineTop(int line);
    float getLineBottom(int line);

    /**
     *
     * @param start 开始索引
     * @param end 结束索引。包含索引字符
     */
    String getTxt(int start,int end);
}
