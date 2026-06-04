package com.sjianjun.reader.utils

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.sjianjun.reader.preferences.globalConfig
import io.legado.app.service.TtsEdgeManager
import sjj.novel.view.reader.page.TxtLine
import sjj.novel.view.reader.page.TxtPage
import sjj.alog.Log

/**
 * A delegating TTS service that routes to either system TTS (`TtsUtil`) or Edge TTS (`TtsEdgeManager`)
 * according to `globalConfig.readerTtsEngine`.
 */
object TtsService : BaseTts {
    override val isSpeaking = MutableLiveData(false)

    override var progressChangeCallback: ((TxtLine) -> Unit)? = null
    override var onCompleted: (() -> Unit)? = null

    // underlying instances
    private val system = TtsUtil()
    private val edge = TtsEdgeManager

    private var systemObserver: Observer<Boolean>? = null
    private var edgeObserver: Observer<Boolean>? = null

    var chapterPos: Int = 0

    init {
        // mirror underlying isSpeaking into service isSpeaking
        systemObserver = Observer<Boolean> { isSpeaking.postValue(it) }
        edgeObserver = Observer<Boolean> { isSpeaking.postValue(it) }
        system.isSpeaking.observeForever(systemObserver!!)
        edge.isSpeaking.observeForever(edgeObserver!!)
    }

    private fun current(): BaseTts {
        return if (globalConfig.readerTtsEngine.value == "edge") edge else system
    }

    suspend fun start(chapterPos: Int, pages: List<TxtPage>?, pagePos: Int) {
        this.chapterPos = chapterPos
        start(pages, pagePos)
    }

    override suspend fun start(pages: List<TxtPage>?, pagePos: Int) {
        try {
            val engine = globalConfig.readerTtsEngine.value ?: "default"
            if (engine == "edge") {
                // delegate to Edge manager with voice param
                val voice = globalConfig.readerTtsVoice.value ?: "zh-CN-XiaoxiaoNeural"
                // propagate callbacks
                edge.progressChangeCallback = { line -> progressChangeCallback?.invoke(line) }
                edge.onCompleted = { onCompleted?.invoke() }
                val edgeRate = globalConfig.readerTtsRate.value ?: 50
                edge.start(pages, pagePos, rate = edgeRate, voice = voice)
            } else {
                // system
                system.progressChangeCallback = { line -> progressChangeCallback?.invoke(line) }
                system.onCompleted = { onCompleted?.invoke() }
                system.start(pages, pagePos)
            }
        } catch (e: Exception) {
            Log.e("TtsService start error: ${e.message}")
        }
    }

    override fun stop() {
        try {
            current().stop()
        } catch (_: Exception) {
        }
    }

}

