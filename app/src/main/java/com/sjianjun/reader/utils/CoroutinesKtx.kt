package com.sjianjun.reader.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sjj.alog.Log

fun ViewModel.launchIo(block: suspend () -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            block()
        } catch (e: Exception) {
            Log.e("Error in ViewModel launchOnLifecycle", e)
        }
    }
}