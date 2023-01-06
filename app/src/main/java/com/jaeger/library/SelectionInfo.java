package com.jaeger.library;

/**
 * Created by Jaeger on 16/8/30.
 *
 * Email: chjie.jaeger@gmail.com
 * GitHub: https://github.com/laobie
 */
public class SelectionInfo {
    public int start;
    public int end;
    public boolean select = false;

    @Override
    public String toString() {
        return "SelectionInfo{" +
                "start=" + start +
                ", end=" + end +
                ", select=" + select +
                '}';
    }
}
