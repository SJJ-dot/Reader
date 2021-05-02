package com.sjianjun.reader.matrix.trace;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;

import com.tencent.matrix.util.MatrixLog;

import sjj.alog.Log;

public class StartUpService extends Service {
    private static String TAG = "Matrix.StartUpService";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("[onStartCommand]");
        return super.onStartCommand(intent, flags, startId);
    }
}
