package com.jaeger.library;

public interface TxtLocation {

    /**
     * 获取行号
     */
    int getLine(int y);
    /**
     * 获取行号
     */
    int getLineForOffset(int offset);
    int getLineStart(int line);
    int getLineStartOffset(int line);
    int getLineEnd(int line);

    /**
     * 获取字符索引
     */
    int getOffset(int x,int y);
    int getHysteresisOffset(int x,int y,int oldOffset,boolean isLeft);



    int getHorizontalRight(int offset);
    int getHorizontalLeft(int offset);

    int getLineTop(int line);
    int getLineBottom(int line);

    /**
     *
     * @param start 开始索引
     * @param end 结束索引。包含索引字符
     */
    String getTxt(int start,int end);
}
