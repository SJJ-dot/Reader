package com.sjianjun.async;

public interface Disposable {
    boolean isDisposed();

    void dispose();
}
