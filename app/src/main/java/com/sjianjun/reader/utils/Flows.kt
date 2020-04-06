package com.sjianjun.reader.utils

import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

interface Flows : CoroutineScope {
    fun <T> Flow<T?>.toLiveData(): MutableLiveData<T?> {
        val liveData = MutableLiveData<T?>()
        launch {
            collect {
                liveData.postValue(it)
            }
        }
        return liveData
    }
}
