package com.sjianjun.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import sjj.alog.Log

@RunWith(AndroidJUnit4::class)
class FlowThreadTest {

    @Test
    fun testflowOn(): Unit = runBlocking {

        flowOf("测试flowOn会不会修改flatMap内部的线程:${Thread.currentThread()}")
            .flatMapMerge {
                val log = "$it \n flatMapMerge所在线程:${Thread.currentThread()}"
                flow {
                    emit("$log \n flow内部所在线程：${Thread.currentThread()}")
                }
            }.flowOn(Dispatchers.IO)
            .collect {
                Log.e("$it \n collect线程：${Thread.currentThread()}")
            }

        flowOf("测试 修改flatMap内部的线程影响 ${Thread.currentThread()}")
            .flatMapMerge {
                flow {
                    emit("$it \n flow内部所在线程：${Thread.currentThread()}")
                }.flowOn(Dispatchers.Default)
            }.collect {
                Log.e("$it \ncollect线程：${Thread.currentThread()}")
            }

        flowOf("测试 修改线程会不会修改同一级所有操作符:${Thread.currentThread()}").map {
            "$it \n 第一个map所在线程：${Thread.currentThread()}"
        }.map {
            "$it \n 第二个map所在线程：${Thread.currentThread()}"
        }.flowOn(Dispatchers.IO).map {
            "$it \n 第三个map所在线程：${Thread.currentThread()}"
        }.flowOn(Dispatchers.Unconfined).collect {
            Log.e(it)
        }

    }
}