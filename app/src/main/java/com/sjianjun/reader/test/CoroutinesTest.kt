package com.sjianjun.reader.test

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import sjj.alog.Log

suspend fun test() {
    flowA().collect {
        flowB(it).collectLatest {
            Log.e(it)
        }
    }
}

fun flowA(): Flow<Int> {
    return flow {
        emit(1)
        delay(5000)
        emit(2)
        delay(5000)
        emit(3)
    }
}

fun flowB(int: Int): Flow<String> {
    return flow {
        while (true) {
            emit("$int b")
            delay(1000)
        }
    }
}