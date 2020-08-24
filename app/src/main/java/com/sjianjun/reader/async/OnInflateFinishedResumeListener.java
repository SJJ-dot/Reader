package com.sjianjun.reader.async;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import com.sjianjun.async.OnInflateFinishedListener;

import org.jetbrains.annotations.NotNull;

public class OnInflateFinishedResumeListener implements OnInflateFinishedListener, LifecycleObserver {
    private Lifecycle lifecycle;
    private OnInflateFinishedListener listener;
    private View view;
    private int layoutRes;
    @Nullable
    private ViewGroup parent;

    public OnInflateFinishedResumeListener(Lifecycle lifecycle, OnInflateFinishedListener listener) {
        this.lifecycle = lifecycle;
        this.listener = listener;
    }


    @Override
    public void onInflateFinished(@NonNull View view, int layoutRes, @NotNull ViewGroup parent) {
        if (lifecycle.getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            listener.onInflateFinished(view, layoutRes, parent);
        } else {
            this.view = view;
            this.layoutRes = layoutRes;
            this.parent = parent;
            lifecycle.addObserver(this);
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    public void onResume() {
        lifecycle.removeObserver(this);
        listener.onInflateFinished(view, layoutRes, parent);
    }


}
