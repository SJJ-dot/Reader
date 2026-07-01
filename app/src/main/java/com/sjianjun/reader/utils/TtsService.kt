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

    // underlying instances; both are recreated on each start cycle to avoid leftover state
    private var system: TtsUtil? = null
    private var edge: TtsEdgeManager? = null

    private var systemObserver: Observer<Boolean>? = null
    private var edgeObserver: Observer<Boolean>? = null

    var chapterPos: Int = 0

    private fun currentEngine(): String = globalConfig.readerTtsEngine.value ?: "default"

    private fun ensureSystem(): TtsUtil {
        system?.let { return it }
        val created = TtsUtil()
        val obs = Observer<Boolean> { isSpeaking.postValue(it) }
        systemObserver = obs
        created.isSpeaking.observeForever(obs)
        system = created
        return created
    }

    private fun ensureEdge(): TtsEdgeManager {
        edge?.let { return it }
        val created = TtsEdgeManager()
        val obs = Observer<Boolean> { isSpeaking.postValue(it) }
        edgeObserver = obs
        created.isSpeaking.observeForever(obs)
        edge = created
        return created
    }

    suspend fun start(chapterPos: Int, pages: List<TxtPage>?, pagePos: Int) {
        this.chapterPos = chapterPos
        start(pages, pagePos)
    }

    override suspend fun start(pages: List<TxtPage>?, pagePos: Int) {
        try {
            if (currentEngine() == "edge") {
                val voice = globalConfig.readerTtsVoice.value ?: "zh-CN-XiaoxiaoNeural"
                val e = ensureEdge()
                e.progressChangeCallback = { line -> progressChangeCallback?.invoke(line) }
                e.onCompleted = { onCompleted?.invoke() }
                val edgeRate = globalConfig.readerTtsRate.value ?: 50
                e.start(pages, pagePos, rate = edgeRate, voice = voice)
            } else {
                val sys = ensureSystem()
                sys.progressChangeCallback = { line -> progressChangeCallback?.invoke(line) }
                sys.onCompleted = { onCompleted?.invoke() }
                sys.start(pages, pagePos)
            }
        } catch (e: Exception) {
            Log.e("TtsService start error: ${e.message}")
        }
    }

    override fun stop() {
        try {
            system?.release()
        } catch (_: Exception) {
        }
        system?.let {
            systemObserver?.let { obs ->
                try { it.isSpeaking.removeObserver(obs) } catch (_: Throwable) {}
            }
        }
        systemObserver = null
        system = null

        try {
            edge?.release()
        } catch (_: Exception) {
        }
        edge?.let {
            edgeObserver?.let { obs ->
                try { it.isSpeaking.removeObserver(obs) } catch (_: Throwable) {}
            }
        }
        edgeObserver = null
        edge = null
        isSpeaking.postValue(false)
    }

    override fun release() {
        stop()
    }

}

