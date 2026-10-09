package io.legado.app.service

import android.media.MediaDataSource
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.utils.BaseTts
import com.sjianjun.reader.utils.MD5Utils
import com.sjianjun.reader.utils.TtsSpeakLine
import com.sjianjun.reader.utils.TtsTextLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import sjj.alog.Log
import sjj.novel.view.reader.page.TxtLine
import sjj.novel.view.reader.page.TxtPage
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Edge TTS 播放管理器：
 * - 每个实例使用同一个 EdgeSpeakFetch，stop/release 时释放
 * - 播放和缓存分离为两个协程任务
 * - 缓存任务：从第一段开始顺序合成，数据写入 StreamingMediaDataSource
 * - 播放任务：等待 streamingSources 有数据后开始播放，播完取下一段
 * - 不使用 audioCache（ByteArray），统一用 StreamingMediaDataSource
 */
class TtsEdgeManager : BaseTts {
    private val keyParagraphMap = HashMap<String, List<TtsSpeakLine>>()
    private var mediaPlayer: MediaPlayer? = null
    private var playQueue = ConcurrentLinkedDeque<StreamingMediaDataSource>()
    private var cacheJob: Job? = null
    private var playJob: Job? = null
    private var currentKey: String? = null
    var playbackRate = 1.0f
    private val playerLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stopped = AtomicBoolean(false)

    /** EdgeSpeakFetch 实例，整个 TtsEdgeManager 生命周期共用 */
    private val edgeFetch = EdgeSpeakFetch()

    private var estimateRunnable: Runnable? = null
    private var estimateStartTime = 0L
    private var estimateLines: List<TtsSpeakLine>? = null
    private var estimateCumDurations: LongArray? = null
    private var baseMsPerWeight: Long = 200L

    override val isSpeaking = MutableLiveData(false)
    override var progressChangeCallback: ((TxtLine) -> Unit)? = null
    override var paragraphChangeCallback: ((Int) -> Unit)? = null
    override var onCompleted: (() -> Unit)? = null
    override var onParagraphsCompleted: (() -> Unit)? = null

    /**
     * true 表示当前会话是段落模式（前台朗读服务），完成回调走 onParagraphsCompleted
     */
    private var paragraphMode = false

    /**
     * 流式 MediaDataSource：数据到达即可播放，readAt 在数据不足时阻塞等待。
     */
    private class StreamingMediaDataSource(val key: String) : MediaDataSource() {
        private val chunks = ArrayList<ByteArray>()
        private var totalSize = 0L
        private var done = false
        private val lock = Object()
        private var flatBytes: ByteArray? = null

        fun feed(data: ByteArray) {
            synchronized(lock) {
                chunks.add(data)
                totalSize += data.size
                flatBytes = null
                lock.notifyAll()
            }
        }

        fun markDone() {
            synchronized(lock) {
                done = true
                lock.notifyAll()
            }
        }

        val isDone: Boolean get() = synchronized(lock) { done }

        /** 是否有数据可以开始播放 */
        val hasData: Boolean get() = synchronized(lock) { totalSize > 0 }

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            synchronized(lock) {
                val pos = position.toInt()
                while (totalSize < pos + 1 && !done) {
                    lock.wait()
                }
                if (pos >= totalSize) return -1
                val available = (totalSize - pos).toInt()
                val toRead = minOf(size, available)
                if (flatBytes == null) {
                    val baos = ByteArrayOutputStream()
                    for (chunk in chunks) baos.write(chunk)
                    flatBytes = baos.toByteArray()
                }
                System.arraycopy(flatBytes, pos, buffer, offset, toRead)
                return toRead
            }
        }

        override fun getSize(): Long = synchronized(lock) { if (done) totalSize else -1 }
        override fun close() {
            markDone()
        }
    }

    private fun cancelEstimate() {
        try {
            estimateRunnable?.let { mainHandler.removeCallbacks(it) }
        } catch (_: Throwable) {
        }
        estimateRunnable = null
        estimateStartTime = 0L
        estimateLines = null
        estimateCumDurations = null
    }

    private fun startEstimateForLines(lines: List<TtsSpeakLine>, totalDurationMs: Int) {
        cancelEstimate()
        estimateLines = lines
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
                    if (idx >= cumNow.size) return
                    if (idx != lastIndex) {
                        lastIndex = idx
                        try {
                            notifyProgress(linesNow[idx].tag)
                        } catch (_: Throwable) {
                        }
                    }
                    mainHandler.postDelayed(this, 150)
                } catch (_: Throwable) {
                }
            }
        }
        mainHandler.postDelayed(estimateRunnable!!, 150)
    }

    /**
     * 播放任务：从 playQueue 取 StreamingMediaDataSource，等待有数据后播放。
     * 播完一段取下一段，队列空且缓存任务结束则完成。
     */
    private fun startPlayTask() {
        playJob = GlobalScope.launch(Dispatchers.Main) {
            while (!stopped.get()) {
                val ds = playQueue.pollFirst()
                if (ds == null) {
                    if (cacheJob?.isActive != true) break
                    kotlinx.coroutines.delay(50)
                    continue
                }
                while (!stopped.get() && !ds.hasData && !ds.isDone) {
                    kotlinx.coroutines.delay(20)
                }
                if (stopped.get()) break
                if (!ds.hasData && ds.isDone) continue
                playSegment(ds)
            }
            if (!stopped.get()) {
                isSpeaking.postValue(false)
                notifyCompleted()
            }
        }
    }

    /**
     * 回传进度：逐行模式回传行对象，段落模式回传段落序号
     */
    private fun notifyProgress(tag: Any) {
        when (tag) {
            is TxtLine -> progressChangeCallback?.invoke(tag)
            is Int -> paragraphChangeCallback?.invoke(tag)
        }
    }

    private fun notifyCompleted() {
        if (paragraphMode) {
            onParagraphsCompleted?.invoke()
        } else {
            onCompleted?.invoke()
        }
    }

    /**
     * 播放一个段落，阻塞直到播放完成或停止。
     */
    private suspend fun playSegment(ds: StreamingMediaDataSource) {
        currentKey = ds.key
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
            mainHandler.post {
                synchronized(playerLock) {
                    if (stopped.get()) {
                        cont.resume(Unit) {}
                        return@post
                    }
                    try {
                        mediaPlayer?.let {
                            try {
                                it.stop()
                            } catch (_: Throwable) {
                            }
                            try {
                                it.release()
                            } catch (_: Throwable) {
                            }
                        }
                        mediaPlayer = null

                        val mp = MediaPlayer()
                        mediaPlayer = mp
                        mp.setDataSource(ds)

                        mp.setOnPreparedListener { player ->
                            try {
                                player.playbackParams = player.playbackParams.setSpeed(playbackRate)
                            } catch (_: Throwable) {
                            }
                            try {
                                val lines = keyParagraphMap[ds.key]
                                if (!lines.isNullOrEmpty()) {
                                    startEstimateForLines(lines, if (player.duration > 0) player.duration else 0)
                                } else {
                                    cancelEstimate()
                                }
                            } catch (_: Throwable) {
                                cancelEstimate()
                            }
                            isSpeaking.postValue(true)
                            try {
                                player.start()
                            } catch (_: Throwable) {
                                cancelEstimate(); if (cont.isActive) cont.resume(Unit) {}
                            }
                        }

                        mp.setOnCompletionListener {
                            cancelEstimate()
                            if (cont.isActive) cont.resume(Unit) {}
                        }

                        mp.setOnErrorListener { _, _, _ ->
                            cancelEstimate()
                            if (cont.isActive) cont.resume(Unit) {}
                            true
                        }

                        try {
                            mp.prepareAsync()
                        } catch (e: IllegalStateException) {
                            Log.e("TtsEdgeManager prepareAsync: ${e.message}")
                            try {
                                mp.release()
                            } catch (_: Throwable) {
                            }
                            mediaPlayer = null
                            if (cont.isActive) cont.resume(Unit) {}
                        }
                    } catch (e: Exception) {
                        try {
                            mediaPlayer?.release()
                        } catch (_: Throwable) {
                        }
                        mediaPlayer = null
                        if (cont.isActive) cont.resume(Unit) {}
                    }
                }
            }
        }
        // 播放结束，释放 MediaPlayer
        try {
            mediaPlayer?.stop()
        } catch (_: Throwable) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Throwable) {
        }
        mediaPlayer = null
    }

    override fun stop() {
        stopped.set(true)
        try {
            cacheJob?.cancel()
        } catch (_: Throwable) {
        }
        try {
            playJob?.cancel()
        } catch (_: Throwable) {
        }
        cacheJob = null
        playJob = null
        cancelEstimate()
        try {
            mediaPlayer?.stop()
        } catch (_: Throwable) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Throwable) {
        }
        mediaPlayer = null
        playQueue.clear()
        playQueue = ConcurrentLinkedDeque()
        currentKey = null
        try {
            edgeFetch.release()
        } catch (_: Throwable) {
        }
        if (isSpeaking.value != false) {
            isSpeaking.postValue(false)
        }
    }

    override fun release() {
        stop()
    }

    /**
     * 启动播放：创建缓存任务和播放任务。
     * 缓存任务从第一段开始顺序合成，写入 StreamingMediaDataSource。
     * 播放任务等待数据到达后开始播放，播完取下一段。
     */
    fun start(pages: List<TxtPage>?, pagePos: Int, rate: Int, voice: String = "zh-CN-XiaoxiaoNeural") {
        if (pages.isNullOrEmpty() || pagePos < 0 || pagePos >= pages.size) return
        stop()
        stopped.set(false)
        paragraphMode = false
        isSpeaking.postValue(true)

        val paragraphs = ArrayList<List<TtsSpeakLine>>()
        var paragraphLines = mutableListOf<TtsSpeakLine>()
        var isTitle = false
        for (pos in pagePos until pages.size) {
            val page = pages[pos]
            page.lines.forEach { line ->
                if (line.isTitle) {
                    isTitle = true
                    paragraphLines.add(TtsSpeakLine(line.txt, line))
                } else {
                    if (isTitle) {
                        isTitle = false
                        if (paragraphLines.isNotEmpty()) {
                            paragraphs.add(paragraphLines)
                            paragraphLines = mutableListOf()
                        }
                    }
                    paragraphLines.add(TtsSpeakLine(line.txt, line))
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

        // 缓存任务：顺序合成每段，写入 StreamingMediaDataSource，加入 playQueue
        cacheJob = GlobalScope.launch(Dispatchers.IO) {
            for (para in paragraphs) {
                if (stopped.get()) return@launch
                val text = para.joinToString(separator = "") { line -> line.txt }
                val key = MD5Utils.md5Encode(text + "_" + rate + "_" + voice)
                keyParagraphMap[key] = para

                val ds = StreamingMediaDataSource(key)
                keyParagraphMap[key] = para
                playQueue.add(ds)

                // 合成并喂入数据
                try {
                    val input = edgeFetch.synthesizeText(text, rate, voice)
                    val buf = ByteArray(8 * 1024)
                    var read = input.read(buf)
                    while (read >= 0) {
                        if (stopped.get()) return@launch
                        if (read > 0) ds.feed(buf.copyOf(read))
                        read = input.read(buf)
                    }
                } catch (e: Exception) {
                    Log.e("synthesis feed error: $key", e)
                } finally {
                    ds.markDone()
                }
            }
        }

        // 播放任务：等待数据到达后播放
        startPlayTask()
    }

    override suspend fun start(pages: List<TxtPage>?, pagePos: Int) {
        start(pages, pagePos, rate = 12, voice = "zh-CN-XiaoxiaoNeural")
    }

    /**
     * 段落模式：只有纯文本，没有排版数据，进度以段落序号回传（前台朗读服务使用）
     */
    fun startParagraphs(paragraphs: List<String>, startIndex: Int, rate: Int, voice: String = "zh-CN-XiaoxiaoNeural"): Boolean {
        if (paragraphs.isEmpty() || startIndex < 0 || startIndex >= paragraphs.size) return false
        stop()
        stopped.set(false)
        paragraphMode = true
        isSpeaking.postValue(true)

        val units = ArrayList<List<TtsSpeakLine>>()
        for (index in startIndex until paragraphs.size) {
            val text = paragraphs[index]
            if (text.isBlank()) continue
            units.add(listOf(TtsSpeakLine(text, index)))
        }

        // 缓存任务：顺序合成每段，写入 StreamingMediaDataSource，加入 playQueue
        cacheJob = GlobalScope.launch(Dispatchers.IO) {
            for (unit in units) {
                if (stopped.get()) return@launch
                val text = unit.joinToString(separator = "") { line -> line.txt }
                // 诊断：打印真正送进在线语音合成的文本（logcat 过滤 TTS_TEXT）
                TtsTextLogger.log(text, unit.firstOrNull()?.tag as? Int)
                val key = MD5Utils.md5Encode(text + "_" + rate + "_" + voice)
                keyParagraphMap[key] = unit

                val ds = StreamingMediaDataSource(key)
                playQueue.add(ds)

                // 合成并喂入数据
                try {
                    val input = edgeFetch.synthesizeText(text, rate, voice)
                    val buf = ByteArray(8 * 1024)
                    var read = input.read(buf)
                    while (read >= 0) {
                        if (stopped.get()) return@launch
                        if (read > 0) ds.feed(buf.copyOf(read))
                        read = input.read(buf)
                    }
                } catch (e: Exception) {
                    Log.e("synthesis feed error: $key", e)
                } finally {
                    ds.markDone()
                }
            }
        }

        startPlayTask()
        return true
    }

    override suspend fun startParagraphs(paragraphs: List<String>, startIndex: Int): Boolean {
        return startParagraphs(paragraphs, startIndex, rate = 12, voice = "zh-CN-XiaoxiaoNeural")
    }

}
