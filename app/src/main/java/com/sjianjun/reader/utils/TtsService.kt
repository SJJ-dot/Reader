package com.sjianjun.reader.utils

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.tts.TtsPlaybackController
import io.legado.app.service.TtsEdgeManager
import sjj.novel.view.reader.page.TxtLine
import sjj.novel.view.reader.page.TxtPage
import sjj.alog.Log

/**
 * A delegating TTS service that routes to either system TTS (`TtsUtil`) or Edge TTS (`TtsEdgeManager`)
 * according to `globalConfig.readerTtsEngine`.
 *
 * 两种朗读模式共用本对象：
 * - 逐行模式：阅读页/网页阅读模式把排版好的页面数据传进来（[start]）
 * - 段落模式：前台朗读服务只传纯文本（[startParagraphs]）
 */
object TtsService : BaseTts {
    override val isSpeaking = MutableLiveData(false)

    override var progressChangeCallback: ((TxtLine) -> Unit)? = null
    override var paragraphChangeCallback: ((Int) -> Unit)? = null
    override var segmentProgressCallback: ((Float) -> Unit)? = null
    override var onPlaybackStarted: (() -> Unit)? = null
    override var onCompleted: (() -> Unit)? = null
    override var onParagraphsCompleted: (() -> Unit)? = null

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
        created.progressChangeCallback = { line -> progressChangeCallback?.invoke(line) }
        created.paragraphChangeCallback = { index -> paragraphChangeCallback?.invoke(index) }
        created.segmentProgressCallback = { progress -> segmentProgressCallback?.invoke(progress) }
        created.onPlaybackStarted = { onPlaybackStarted?.invoke() }
        created.onCompleted = { onCompleted?.invoke() }
        created.onParagraphsCompleted = { onParagraphsCompleted?.invoke() }
        system = created
        return created
    }

    private fun ensureEdge(): TtsEdgeManager {
        edge?.let { return it }
        val created = TtsEdgeManager()
        val obs = Observer<Boolean> { isSpeaking.postValue(it) }
        edgeObserver = obs
        created.isSpeaking.observeForever(obs)
        created.progressChangeCallback = { line -> progressChangeCallback?.invoke(line) }
        created.paragraphChangeCallback = { index -> paragraphChangeCallback?.invoke(index) }
        created.segmentProgressCallback = { progress -> segmentProgressCallback?.invoke(progress) }
        created.onPlaybackStarted = { onPlaybackStarted?.invoke() }
        created.onCompleted = { onCompleted?.invoke() }
        created.onParagraphsCompleted = { onParagraphsCompleted?.invoke() }
        edge = created
        return created
    }

    suspend fun start(chapterPos: Int, pages: List<TxtPage>?, pagePos: Int) {
        this.chapterPos = chapterPos
        start(pages, pagePos)
    }

    override suspend fun start(pages: List<TxtPage>?, pagePos: Int) {
        // 逐行模式与前台朗读服务互斥，避免两个引擎同时发声
        TtsPlaybackController.stopIfActive()
        try {
            if (currentEngine() == "edge") {
                val voice = globalConfig.readerTtsVoice.value ?: "zh-CN-XiaoxiaoNeural"
                val edgeRate = globalConfig.readerTtsRate.value ?: 50
                ensureEdge().start(pages, pagePos, rate = edgeRate, voice = voice)
            } else {
                ensureSystem().start(pages, pagePos)
            }
        } catch (e: Exception) {
            Log.e("TtsService start error: ${e.message}")
        }
    }

    override suspend fun startParagraphs(paragraphs: List<String>, startIndex: Int): Boolean {
        val started = try {
            if (currentEngine() == "edge") {
                val voice = globalConfig.readerTtsVoice.value ?: "zh-CN-XiaoxiaoNeural"
                val edgeRate = globalConfig.readerTtsRate.value ?: 50
                ensureEdge().startParagraphs(paragraphs, startIndex, rate = edgeRate, voice = voice)
            } else {
                ensureSystem().startParagraphs(paragraphs, startIndex)
            }
        } catch (e: Exception) {
            Log.e("TtsService startParagraphs error: ${e.message}")
            false
        }
        return started
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
