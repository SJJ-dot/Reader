package com.sjianjun.reader.utils

import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

interface Flows : CoroutineScope {
    fun <T> Flow<T?>.toLiveData(): MutableLiveData<T?> {
        val liveData = MutableLiveData<T?>()
        launch {
            collectLatest {
                liveData.postValue(it)
            }
        }
        return liveData
    }
}

fun <T> Flow<T>.debounce(timeoutMillis: Int = 1000): Flow<T> {
    return debounce(timeoutMillis.toLong())
}