package com.sjianjun.reader.view

import com.victor.loading.rotate.RotateLoading

var RotateLoading.isLoading: Boolean
    get() = isStart
    set(value) {
        if (value && !isStart) {
            start()
        } else if (!value && isStart) {
            stop()
        }
    }