package io.legado.app.service

import android.media.MediaPlayer
import android.os.Build
import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.utils.BaseTts
import com.sjianjun.reader.App
import io.legado.app.utils.MD5Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import android.os.Handler
import android.os.Looper
import sjj.alog.Log
import sjj.novel.view.reader.page.TxtLine
import sjj.novel.view.reader.page.TxtPage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 简化的 Edge TTS 播放管理器：
 * - 保留内存缓存（Map<String, ByteArray>）
 * - 支持顺序播放缓存中的音频片段
 * - 提供与 TtsUtil 类似的接口：playKeys / pause / resume / stop / cache / isCached / clear
 */
object TtsEdgeManager : BaseTts {
    private val audioCache = HashMap<String, ByteArray>()
    private val keyParagraphMap = HashMap<String, List<TxtLine>>()
    private var mediaPlayer: MediaPlayer? = null
    private var playQueue = ConcurrentLinkedDeque<String>()
    private var currentKey: String? = null
    var playbackRate = 1.0f
    private val playerLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentWriteJob: Job? = null
    private val stopped = AtomicBoolean(false)
    // estimator for per-line progress callbacks
    private var estimateRunnable: Runnable? = null
    private var estimateStartTime = 0L
    private var estimateLines: List<TxtLine>? = null
    private var estimateCumDurations: LongArray? = null
    private var baseMsPerWeight: Long = 200L

    // Compatibility with TtsUtil usage
    override val isSpeaking = MutableLiveData(false)
    override var progressChangeCallback: ((TxtLine) -> Unit)? = null
    override var onCompleted: (() -> Unit)? = null

    /**
     * Synthesize the given text using EdgeSpeakFetch and cache the resulting mp3 bytes.
     * Returns the cache key (md5 of text+rate).
     */
    fun synthesizeAndCache(text: String, rate: Int, voice: String = "zh-CN-XiaoxiaoNeural", paragraphLines: List<TxtLine>? = null): String {
        val key = MD5Utils.md5Encode(text + "_" + rate + "_" + voice)
        if (audioCache.containsKey(key)) {
            paragraphLines?.let { keyParagraphMap[key] = it }
            return key
        }

        try {
            val input: InputStream = EdgeSpeakFetch().synthesizeText(text, rate, voice)
            val baos = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var read = input.read(buffer)
            while (read >= 0) {
                if (read > 0) baos.write(buffer, 0, read)
                read = input.read(buffer)
            }
            audioCache[key] = baos.toByteArray()
            paragraphLines?.let { keyParagraphMap[key] = it }
        } catch (e: Exception) {
            // synthesis failed; leave uncached
        }
        return key
    }

    fun removeCache(key: String) {
        audioCache.remove(key)
        // remove any temp file if exists
        try {
            val f = File(App.app.cacheDir, "tts_$key.mp3")
            if (f.exists()) f.delete()
        } catch (_: Throwable) {}
    }

    fun removeAllCache() {
        audioCache.clear()
        // clear temp files
        try {
            App.app.cacheDir.listFiles()?.forEach { f -> if (f.name.startsWith("tts_") ) f.delete() }
        } catch (_: Throwable) {}
    }

    private fun cancelEstimate() {
        try {
            estimateRunnable?.let { mainHandler.removeCallbacks(it) }
        } catch (_: Throwable) {}
        estimateRunnable = null
        estimateStartTime = 0L
        estimateLines = null
        estimateCumDurations = null
    }

    private fun startEstimateForLines(lines: List<TxtLine>, totalDurationMs: Int) {
        cancelEstimate()
        estimateLines = lines
        // build simple weight model per line
        val weights = LongArray(lines.size)
        var totalWeight = 0L
        for (i in lines.indices) {
            var w = 0f
            lines[i].txt.forEach {
                w += when (it) {
                    ',', '，', '、' -> 2f
                    '.', '。', '!', '！', '?', '？', ';', '；' -> 2.2f
                    ' ', '\t', '\n' -> 0.6f
                    else -> 1.0f
                }
            }
            val wi = w.toLong()
            weights[i] = if (wi <= 0) 1L else wi
            totalWeight += weights[i]
        }

        val cum = LongArray(lines.size)
        if (totalDurationMs > 1000 && totalWeight > 0) {
            var acc = 0L
            for (i in lines.indices) {
                acc += (totalDurationMs * weights[i] / totalWeight)
                cum[i] = acc
            }
        } else {
            var acc = 0L
            for (i in lines.indices) {
                acc += weights[i] * baseMsPerWeight
                cum[i] = acc
            }
        }
        estimateCumDurations = cum
        estimateStartTime = System.currentTimeMillis()

        estimateRunnable = object : Runnable {
            var lastIndex = -1
            override fun run() {
                try {
                    val linesNow = estimateLines ?: return
                    val cumNow = estimateCumDurations ?: return
                    val elapsed = System.currentTimeMillis() - estimateStartTime
                    var idx = 0
                    while (idx < cumNow.size && elapsed > cumNow[idx]) idx++
                    if (idx >= cumNow.size) {
                        return
                    }
                    if (idx != lastIndex) {
                        lastIndex = idx
                        try {
                            progressChangeCallback?.invoke(linesNow[idx])
                        } catch (_: Throwable) {}
                    }
                    mainHandler.postDelayed(this, 150)
                } catch (_: Throwable) {}
            }
        }
        mainHandler.postDelayed(estimateRunnable!!, 150)
    }

    private fun playNext() {
        if (stopped.get()) {
            return
        }
        val prevKey = currentKey
        val key = playQueue.pollFirst()
        // remove cache for the previously played segment
        prevKey?.let {
            try {
                removeCache(it)
            } catch (_: Exception) {}
        }

        if (key == null) {
            // finished
            stop()
            try {
                isSpeaking.value = false
                onCompleted?.invoke()
            } catch (_: Exception) {}
            return
        }
        // playKey will handle background file write and ensure player is prepared on main thread
        playKey(key)
    }

    private fun playKey(key: String) {
        currentKey = key
        val bytes = audioCache[key]
        if (bytes == null) {
            playNext()
            return
        }

        // write temp file off-main then prepare MediaPlayer on main thread
        currentWriteJob = GlobalScope.launch(Dispatchers.IO) {
            try {
                val tmp = File(App.app.cacheDir, "tts_$key.mp3")
                tmp.writeBytes(bytes)

                // now prepare player on main thread
                if (stopped.get()) {
                    return@launch
                }
                mainHandler.post {
                    synchronized(playerLock) {
                        if (stopped.get()) return@post
                        try {
                            // release previous player
                            mediaPlayer?.let {
                                try { it.stop() } catch (_: Throwable) {}
                                try { it.release() } catch (_: Throwable) {}
                            }
                            mediaPlayer = null

                            val mp = MediaPlayer()
                            mediaPlayer = mp
                            mp.setDataSource(tmp.absolutePath)
                            mp.setOnPreparedListener { player ->
                                try {
                                    player.playbackParams = player.playbackParams.setSpeed(playbackRate)
                                } catch (_: Throwable) {}
                                // start per-line estimator based on audio duration
                                try {
                                    val lines = keyParagraphMap[key]
                                    if (!lines.isNullOrEmpty()) {
                                        startEstimateForLines(lines, player.duration)
                                    } else {
                                        cancelEstimate()
                                    }
                                } catch (_: Throwable) {
                                    cancelEstimate()
                                }
                                isSpeaking.postValue(true)
                                try { player.start() } catch (_: Throwable) { cancelEstimate(); playNext() }
                            }
                            mp.setOnCompletionListener { _ ->
                                // cancel estimator and schedule next on main thread
                                cancelEstimate()
                                mainHandler.post { playNext() }
                            }
                            mp.setOnErrorListener { _, _, _ ->
                                cancelEstimate()
                                mainHandler.post { playNext() }
                                true
                            }

                            try {
                                mp.prepareAsync()
                            } catch (e: IllegalStateException) {
                                Log.e("TtsEdgeManager prepareAsync IllegalState: ${e.message}")
                                try { mp.release() } catch (_: Throwable) {}
                                mediaPlayer = null
                                playNext()
                            }
                        } catch (e: Exception) {
                            // fallback: try next
                            try { mediaPlayer?.release() } catch (_: Throwable) {}
                            mediaPlayer = null
                            playNext()
                        }
                    }
                }

            } catch (e: IOException) {
                // skip and play next
                playNext()
            }
        }
    }

    override fun stop() {
        try {
            stopped.set(true)
            // cancel any pending write job
            try { currentWriteJob?.cancel() } catch (_: Throwable) {}
            currentWriteJob = null
            cancelEstimate()
            mediaPlayer?.stop()
        } catch (_: Throwable) {}
        try { mediaPlayer?.release() } catch (_: Throwable) {}
        mediaPlayer = null
        playQueue.clear()
        playQueue = ConcurrentLinkedDeque()
        currentKey = null
        if (isSpeaking.value != false) {
            isSpeaking.postValue(false)
        }
        // clear all cached audio when explicitly stopping
        try {
            removeAllCache()
        } catch (_: Exception) {}
    }

    /**
     * Start speaking pages from pagePos using Edge TTS.
     * This will synthesize paragraphs in background and enqueue them for playback.
     */
    fun start(pages: List<TxtPage>?, pagePos: Int, rate: Int = 12, voice: String = "zh-CN-XiaoxiaoNeural") {
        if (pages.isNullOrEmpty() || pagePos < 0 || pagePos >= pages.size) return
        stop()
        // allow new plays
        stopped.set(false)
        isSpeaking.postValue(true)

        // build paragraphs similarly to TtsUtil
        val paragraphs = ArrayList<List<TxtLine>>()
        var paragraphLines = mutableListOf<TxtLine>()
        var isTitle = false
        for (pos in pagePos until pages.size) {
            val page = pages[pos]
            page.lines.forEach { line ->
                if (line.isTitle) {
                    isTitle = true
                    paragraphLines.add(line)
                } else {
                    if (isTitle) {
                        isTitle = false
                        if (paragraphLines.isNotEmpty()) {
                            paragraphs.add(paragraphLines)
                            paragraphLines = mutableListOf()
                        }
                    }
                    paragraphLines.add(line)
                    if (line.isParaEnd) {
                        if (paragraphLines.isNotEmpty()) {
                            paragraphs.add(paragraphLines)
                            paragraphLines = mutableListOf()
                        }
                    }
                }
            }
        }
        if (paragraphLines.isNotEmpty()) paragraphs.add(paragraphLines)

        val playQueue = this.playQueue
        // synthesize and enqueue sequentially. Start playback as soon as the first paragraph is ready.
        GlobalScope.launch(Dispatchers.IO) {
            try {
                for (para in paragraphs) {
                    val text = para.joinToString(separator = "") { line -> line.txt }
                    val key = synthesizeAndCache(text, rate, voice, para)
                    if (stopped.get()) {
                        return@launch
                    }
                    // enqueue for playback
                    playQueue.add(key)
                    // if nothing is playing yet, start
                    if (mediaPlayer == null) playNext()
                }
            } catch (_: Exception) {
            }
        }
    }

    // implement BaseTts suspend start by delegating to non-suspend start
    override suspend fun start(pages: List<TxtPage>?, pagePos: Int) {
        start(pages, pagePos, rate = 12, voice = "zh-CN-XiaoxiaoNeural")
    }

}



