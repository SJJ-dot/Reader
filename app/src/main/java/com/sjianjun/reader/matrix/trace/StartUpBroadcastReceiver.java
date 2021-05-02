package com.sjianjun.reader.matrix.trace;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.sjianjun.reader.App;
import com.tencent.matrix.util.MatrixLog;

import sjj.alog.Log;


public class StartUpBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "Matrix.StartUpBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("[onReceive]");
        App.app.startActivity(new Intent(App.app, TestOtherProcessActivity.class));
    }
}
