package com.sjianjun.async.layoutinflater;

/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.sjianjun.async.Disposable;
import com.sjianjun.async.Logger;
import com.sjianjun.async.OnInflateFinishedListener;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * <p>Helper class for inflating layouts asynchronously. To use, construct
 * an instance of {@link AsyncLayoutInflater} on the UI thread and call
 * {@link #inflate(int, ViewGroup, OnInflateFinishedListener)}. The
 * {@link OnInflateFinishedListener} will be invoked on the UI thread
 * when the inflate request has completed.
 *
 * <p>This is intended for parts of the UI that are created lazily or in
 * response to user interactions. This allows the UI thread to continue
 * to be responsive & animate while the relatively heavy inflate
 * is being performed.
 *
 * <p>For a layout to be inflated asynchronously it needs to have a parent
 * whose {@link ViewGroup#generateLayoutParams(AttributeSet)} is thread-safe
 * and all the Views being constructed as part of inflation must not create
 * any {@link Handler}s or otherwise call {@link Looper#myLooper()}. If the
 * layout that is trying to be inflated cannot be constructed
 * asynchronously for whatever reason, {@link AsyncLayoutInflater} will
 * automatically fall back to inflating on the UI thread.
 *
 * <p>NOTE that the inflated View hierarchy is NOT added to the parent. It is
 * equivalent to calling {@link LayoutInflater#inflate(int, ViewGroup, boolean)}
 * with attachToRoot set to false. Callers will likely want to call
 * {@link ViewGroup#addView(View)} in the {@link OnInflateFinishedListener}
 * callback at a minimum.
 *
 * <p>This inflater does not support setting a {@link LayoutInflater.Factory}
 * nor {@link LayoutInflater.Factory2}. Similarly it does not support inflating
 * layouts that contain fragments.
 */
public final class AsyncLayoutInflater {

    LayoutInflater mInflater;
    Handler mHandler;
    InflateThread mInflateThread;

    Logger logger;


    public AsyncLayoutInflater(LayoutInflater inflater, Logger logger) {
        mInflater = inflater;
        this.logger = logger;
        mHandler = new Handler(mHandlerCallback);
        mInflateThread = InflateThread.getInstance();
    }

    public Disposable inflate(int resid, ViewGroup parent, OnInflateFinishedListener callback) {
        InflateRequest request = new InflateRequest();
        request.inflater = this;
        request.logger = logger;
        request.resid = resid;
        request.parent = parent;
        request.callback = callback;
        request.set(false);
        mInflateThread.enqueue(request);
        return request;
    }


    private Callback mHandlerCallback = new Callback() {
        @Override
        public boolean handleMessage(@NotNull Message msg) {
            final InflateRequest request = (InflateRequest) msg.obj;
            //检查是否被取消
            if (request.get()) {
                return true;
            }

            Looper.myQueue().addIdleHandler(new MessageQueue.IdleHandler() {
                @Override
                public boolean queueIdle() {
                    //检查是否被取消
                    if (request.get()) {
                        return false;
                    }

                    if (request.view == null) {
                        request.view = mInflater.inflate(
                                request.resid, request.parent, false);
                        //检查是否被取消
                        if (request.get()) {
                            return false;
                        }
                    }
                    try {
                        request.callback.onInflateFinished(request.view, request.resid, request.parent);
                    } catch (Throwable e) {
                        if (logger != null) {
                            logger.log("onInflateFinished error:" + e.getMessage(), e);
                        }
                    }

                    return false;
                }
            });
            return true;
        }
    };

    private static class InflateRequest extends AtomicBoolean implements Disposable {
        AsyncLayoutInflater inflater;
        ViewGroup parent;
        int resid;
        View view;
        OnInflateFinishedListener callback;
        @Nullable
        Logger logger;

        InflateRequest() {
        }

        @Override
        public boolean isDisposed() {
            return get();
        }

        @Override
        public void dispose() {
            set(true);
        }
    }

    private static class InflateThread extends Thread {
        private static final InflateThread sInstance;

        static {
            sInstance = new InflateThread();
            sInstance.start();
        }

        public static InflateThread getInstance() {
            return sInstance;
        }

        private ArrayBlockingQueue<InflateRequest> mQueue = new ArrayBlockingQueue<>(10);

        // Extracted to its own method to ensure locals have a constrained liveness
        // scope by the GC. This is needed to avoid keeping previous request references
        // alive for an indeterminate amount of time, see b/33158143 for details
        public void runInner() {
            InflateRequest request;
            try {
                request = mQueue.take();
                if (request.logger != null) {
                    request.logger.log("inflate runInner " + request.get() + "  " + System.identityHashCode(request), null);
                }
            } catch (InterruptedException ex) {
                // Odd, just continue
                return;
            }

            if (request.get()) {
                if (request.logger != null) {
                    request.logger.log("inflate request.get() 1" + request.get() + "  " + request, null);
                }
                return;
            }
            try {
                request.view = request.inflater.mInflater.inflate(
                        request.resid, request.parent, false);
            } catch (RuntimeException ex) {
                // Probably a Looper failure, retry on the UI thread
                if (request.logger != null) {
                    request.logger.log("Failed to inflate resource in the background! Retrying on the UI thread:" + ex.getMessage(), ex);
                }
            }
            if (request.get()) {
                if (request.logger != null) {
                    request.logger.log("inflate request.get() 2", null);
                }
                return;
            }
            if (request.logger != null) {
                request.logger.log("send message", null);
            }
            Message.obtain(request.inflater.mHandler, 0, request).sendToTarget();
        }

        @Override
        public void run() {
            while (true) {
                runInner();
            }
        }

        public void enqueue(InflateRequest request) {
            try {
                mQueue.put(request);
            } catch (InterruptedException e) {
                throw new RuntimeException(
                        "Failed to enqueue async inflate request", e);
            }
        }
    }
}

