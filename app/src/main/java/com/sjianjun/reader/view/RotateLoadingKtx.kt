package com.sjianjun.reader.view


var RotateLoading.isLoading: Boolean
    get() = isStart
    set(value) {
        if (value && !isStart) {
            start()
        } else if (!value && isStart) {
            stop()
        }
    }

