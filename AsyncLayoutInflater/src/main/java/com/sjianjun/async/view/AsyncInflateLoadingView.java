package com.sjianjun.async.view;


import android.content.Context;
import android.util.AttributeSet;
import android.widget.ProgressBar;

import com.sjianjun.async.layoutinflater.R;


public class AsyncInflateLoadingView extends ProgressBar {

    public AsyncInflateLoadingView(Context context) {
        this(context, null);
    }

    public AsyncInflateLoadingView(Context context, AttributeSet attrs) {
        super(context, attrs, R.attr.asyncLoadViewLoadingStyle);
    }
}

