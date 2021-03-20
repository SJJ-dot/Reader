package com.sjianjun.async;

import android.animation.TimeInterpolator;
import android.app.ActionBar;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.sjianjun.async.layoutinflater.R;
import com.sjianjun.async.view.AsyncInflateContainerView;
import com.sjianjun.async.view.AsyncInflateLoadingView;

import static android.widget.RelativeLayout.CENTER_IN_PARENT;

public class AsyncInflateConfig {
    public TimeInterpolator interpolator;
    public Logger logger;
    public boolean fadeIn = true;
    public boolean fadeOut = true;


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

    private TimeInterpolator getInterpolator() {
        if (interpolator == null) {
            interpolator = new AccelerateInterpolator();
        }
        return interpolator;
    }


    public static AsyncInflateConfig config;

    public static AsyncInflateConfig getDef() {
        if (config == null) {
            config = new AsyncInflateConfig();
        }
        return config;
    }

}
