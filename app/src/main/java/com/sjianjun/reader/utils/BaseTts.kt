package com.sjianjun.reader.utils

import androidx.lifecycle.MutableLiveData
import sjj.novel.view.reader.page.TxtLine
import sjj.novel.view.reader.page.TxtPage

/**
 * Common TTS interface implemented by different TTS engines.
 */
interface BaseTts {
    val isSpeaking: MutableLiveData<Boolean>

    var progressChangeCallback: ((TxtLine) -> Unit)?

    var onCompleted: (() -> Unit)?

    suspend fun start(pages: List<TxtPage>?, pagePos: Int)

    fun stop()

}

