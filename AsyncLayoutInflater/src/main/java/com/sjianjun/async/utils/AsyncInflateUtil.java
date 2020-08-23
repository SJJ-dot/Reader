package com.sjianjun.async.utils;

import android.animation.TimeInterpolator;
import android.app.ActionBar;
import android.content.Context;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.sjianjun.async.Disposable;
import com.sjianjun.async.Logger;
import com.sjianjun.async.OnInflateFinishedListener;
import com.sjianjun.async.layoutinflater.AsyncLayoutInflater;
import com.sjianjun.async.layoutinflater.R;
import com.sjianjun.async.view.AsyncInflateContainerView;
import com.sjianjun.async.view.AsyncInflateLoadingView;

import org.jetbrains.annotations.NotNull;

import static android.widget.RelativeLayout.CENTER_IN_PARENT;

public class AsyncInflateUtil {
    public TimeInterpolator interpolator;
    public Logger logger;
    public boolean fadeIn = true;
    public boolean fadeOut = true;

    public Pair<? extends View, Disposable> inflate(AsyncLayoutInflater asyncLayoutInflater, @NotNull ViewGroup parent, int layoutRes, final OnInflateFinishedListener listener) {
        onStartInflateView(parent);
        Disposable disposable = asyncLayoutInflater.inflate(layoutRes, parent, new OnInflateFinishedListener() {
            @Override
            public void onInflateFinished(@NotNull View view, int layoutRes, @NotNull ViewGroup parent) {
                onCompleteInflateView(parent, view);
                listener.onInflateFinished(view, layoutRes, parent);
            }
        });


        return new Pair<>(parent, disposable);
    }

    public Pair<? extends View, Disposable> inflate(Context context, int layoutRes, final OnInflateFinishedListener listener) {
        AsyncLayoutInflater asyncLayoutInflater = new AsyncLayoutInflater(LayoutInflater.from(context), getLogger());
        AsyncInflateContainerView containerView = new AsyncInflateContainerView(context);
        return inflate(asyncLayoutInflater, containerView, layoutRes, listener);
    }


    public void onStartInflateView(ViewGroup parent) {
        AsyncInflateLoadingView loadingView = new AsyncInflateLoadingView(parent.getContext());
        parent.addView(loadingView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        ViewGroup.LayoutParams params = loadingView.getLayoutParams();
        if (params instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) params).gravity = Gravity.CENTER;
            params.width = ActionBar.LayoutParams.WRAP_CONTENT;
            params.height = ActionBar.LayoutParams.WRAP_CONTENT;
        } else if (params instanceof RelativeLayout.LayoutParams) {
            ((RelativeLayout.LayoutParams) params).addRule(CENTER_IN_PARENT);
            params.width = ActionBar.LayoutParams.WRAP_CONTENT;
            params.height = ActionBar.LayoutParams.WRAP_CONTENT;
        }

        parent.setTag(R.id.tag_loading_view, loadingView);

        if (fadeIn) {
            loadingView.animate()
                    .alpha(1f)
                    .setDuration(500)
                    .setInterpolator(getInterpolator())
                    .start();
        }
    }

    public void onCompleteInflateView(final ViewGroup parent, View inflate) {
        if (parent instanceof AsyncInflateContainerView) {
            ((AsyncInflateContainerView) parent).setContentView(inflate, true);
        } else {
            parent.addView(inflate, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        final Object tag = parent.getTag(R.id.tag_loading_view);
        if (tag instanceof View) {

            parent.setTag(R.id.tag_loading_view, null);

            if (fadeOut) {
                ((View) tag).animate()
                        .alpha(0f)
                        .setDuration(500)
                        .setInterpolator(interpolator)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                parent.removeView((View) tag);
                            }
                        }).start();
            } else {
                parent.removeView((View) tag);
            }
        }

        if (fadeIn) {
            inflate.animate()
                    .alpha(1f)
                    .setDuration(500)
                    .setInterpolator(getInterpolator())
                    .start();
        }
    }


    private Logger getLogger() {
        if (logger == null) {
            logger = new Logger() {
                @Override
                public void log(String msg, Throwable e) {
                    Log.e("asyncInflate", msg, e);
                }
            };
        }
        return logger;
    }

    private TimeInterpolator getInterpolator() {
        if (interpolator == null) {
            interpolator = new AccelerateInterpolator();
        }
        return interpolator;
    }

}
