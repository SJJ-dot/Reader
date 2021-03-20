package com.sjianjun.async.utils;

import android.content.Context;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.sjianjun.async.AsyncInflateConfig;
import com.sjianjun.async.Disposable;
import com.sjianjun.async.OnInflateFinishedListener;
import com.sjianjun.async.layoutinflater.AsyncLayoutInflater;
import com.sjianjun.async.view.AsyncInflateContainerView;

import org.jetbrains.annotations.NotNull;

public class AsyncInflateUtil {
    @NotNull
    public AsyncInflateConfig config;

    public AsyncInflateUtil() {
        this(AsyncInflateConfig.getDef());
    }

    public AsyncInflateUtil(@NotNull AsyncInflateConfig config) {
        this.config = config;
    }

    public Pair<? extends View, Disposable> inflate(final AsyncInflateConfig config, AsyncLayoutInflater asyncLayoutInflater, @NotNull ViewGroup parent, int layoutRes, final OnInflateFinishedListener listener) {
        config.onStartInflateView(parent);
        Disposable disposable = asyncLayoutInflater.inflate(layoutRes, parent, new OnInflateFinishedListener() {
            @Override
            public void onInflateFinished(@NotNull View view, int layoutRes, @NotNull ViewGroup parent) {
                config.onCompleteInflateView(parent, view);
                listener.onInflateFinished(view, layoutRes, parent);
            }
        });


        return new Pair<>(parent, disposable);
    }

    public Pair<? extends View, Disposable> inflate(Context context, int layoutRes, final OnInflateFinishedListener listener) {
        AsyncLayoutInflater asyncLayoutInflater = new AsyncLayoutInflater(LayoutInflater.from(context), config.logger);
        AsyncInflateContainerView containerView = new AsyncInflateContainerView(context);
        return inflate(config, asyncLayoutInflater, containerView, layoutRes, listener);
    }


}
