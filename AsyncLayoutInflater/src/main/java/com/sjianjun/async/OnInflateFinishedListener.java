package com.sjianjun.async;

import android.view.View;
import android.view.ViewGroup;

import org.jetbrains.annotations.NotNull;

public interface OnInflateFinishedListener {
    void onInflateFinished(@NotNull View view, int layoutRes,
                           @NotNull ViewGroup parent);
}
