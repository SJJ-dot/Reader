package io.legado.app.service

import android.media.MediaDataSource
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.tts.TtsChapterSplitter
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
    companion object {
        /** 段内进度轮询间隔（Edge 需要联网合成，真实进度只能靠轮询播放位置，间隔小一点让进度更贴近声音） */
        private const val SEGMENT_PROGRESS_TICK_MS = 100L

        /** 合成音频格式为 48kbps 单声道 mp3（CBR），每毫秒 6 字节，可由字节数精确换算时长 */
        private const val BYTES_PER_MS = 6L

        /** 单段音频时长上限（毫秒），超过说明播放器给出的时长不可信 */
        private const val MAX_SEGMENT_MS = 10 * 60 * 1000L

        /** 预加载目标：领先播放的音频总时长至少 5 分钟 */
        private const val MIN_PRELOAD_MS = 5 * 60 * 1000L
    }

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
    private var segmentProgressRunnable: Runnable? = null
    private var estimateStartTime = 0L
    private var estimateLines: List<TtsSpeakLine>? = null
    private var estimateCumDurations: LongArray? = null
    private var baseMsPerWeight: Long = 200L

    override val isSpeaking = MutableLiveData(false)
    override var progressChangeCallback: ((TxtLine) -> Unit)? = null
    override var paragraphChangeCallback: ((Int) -> Unit)? = null
    override var segmentProgressCallback: ((Float) -> Unit)? = null
    override var onPlaybackStarted: (() -> Unit)? = null
    override var onCompleted: (() -> Unit)? = null
    override var onParagraphsCompleted: (() -> Unit)? = null

    /** 段落合成重试用尽且播放已追上：上报原因（上层停止会话并提示"网络合成失败"） */
    var onParagraphsError: ((String) -> Unit)? = null

    /**
     * true 表示当前会话是段落模式（前台朗读服务），完成回调走 onParagraphsCompleted
     */
    private var paragraphMode = false

    /**
     * 待合成段落队列（段落模式）。
     *
     * 支持运行中追加：顺序播放到"下一章"时不重置队列、不重建连接，只是把新段落排到队尾。
     */
    private val synthQueue = java.util.concurrent.LinkedBlockingQueue<List<TtsSpeakLine>>()

    /** 是否还会追加段落（false 且队列排空时合成任务收尾） */
    @Volatile
    private var synthFinished = false

    /**
     * 预加载目标：领先播放的音频**总时长至少 5 分钟**（6 字节/毫秒 → 约 1.8MB），
     * 并且**至少提前合成 1 段**。
     *
     * 合成任务按这个目标提前合成（多出的会进 [EdgeAudioCache]），
     * 这样顺序播放/切章/暂停恢复都有现成音频可用。
     */
    @Volatile
    private var preloadTargetMs = MIN_PRELOAD_MS
    private val preloadTargetBytes: Long get() = preloadTargetMs * EdgeAudioCache.BYTES_PER_MS

    /** 领先播放的已合成段数 / 字节数（合成任务加、播放任务减） */
    private val aheadCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val aheadBytes = java.util.concurrent.atomic.AtomicLong(0)

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

        /** 合成失败（重试用尽）：记录原因，播放到该段时据此决定"停止 + 提示" */
        fun markFailed(message: String) {
            synchronized(lock) {
                errorMessage = message
                done = true
                lock.notifyAll()
            }
        }

        /** 非空表示这一段合成失败（没有音频） */
        @Volatile
        var errorMessage: String? = null
            private set

        val isDone: Boolean get() = synchronized(lock) { done }

        /** 已合成的音频字节数（合成完成时即整段音频大小） */
        val synthesizedBytes: Long get() = synchronized(lock) { totalSize }

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

    private fun cancelSegmentProgress() {
        try {
            segmentProgressRunnable?.let { mainHandler.removeCallbacks(it) }
        } catch (_: Throwable) {
        }
        segmentProgressRunnable = null
    }

    /**
     * 段落模式：按音频真实播放位置上报段内进度。
     *
     * 不用墙钟时间估算的原因：合成/缓冲卡顿时时间照走，估算会明显偏快。
     * 段长优先用播放器给出的时长，拿不到时按「48kbps CBR 已合成字节数 / 6」换算（音质格式固定，换算精确）。
     * 两者都拿不到就不上报，交给前台服务用时间模型兜底。
     */
    private fun startSegmentProgressPoller(ds: StreamingMediaDataSource) {
        cancelSegmentProgress()
        segmentProgressRunnable = object : Runnable {
            override fun run() {
                try {
                    val player = mediaPlayer
                    if (player != null) {
                        val position = player.currentPosition
                        val duration = segmentDurationMs(player, ds)
                        if (duration > 0) {
                            segmentProgressCallback?.invoke(
                                (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                            )
                        }
                    }
                } catch (_: Throwable) {
                }
                mainHandler.postDelayed(this, SEGMENT_PROGRESS_TICK_MS)
            }
        }
        mainHandler.postDelayed(segmentProgressRunnable!!, SEGMENT_PROGRESS_TICK_MS)
    }

    /** 段音频总时长（毫秒）：合成完成时按字节数精确换算，否则用播放器时长（限一个合理范围） */
    private fun segmentDurationMs(player: MediaPlayer, ds: StreamingMediaDataSource): Long {
        if (ds.isDone) {
            val bytes = ds.synthesizedBytes
            if (bytes > 0) return bytes / BYTES_PER_MS
        }
        val duration = player.duration
        return if (duration > 0 && duration <= MAX_SEGMENT_MS) duration.toLong() else -1L
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
            // 给缓存任务一点启动时间：新建 manager 时缓存任务刚 launch 还没真正开跑，
            // 此时队列是空的，会被误判为"没有更多段落"直接结束（表现为切章后没有声音）
            kotlinx.coroutines.delay(50)
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
                if (!ds.hasData && ds.isDone) {
                    val error = ds.errorMessage
                    if (error != null) {
                        // 播放追上了这段、但重试用尽仍然没有音频：停止并上报（上层提示用户）
                        Log.e("TtsEdgeManager 播放到合成失败的段：$error")
                        onParagraphsError?.invoke(error)
                        stop()
                        return@launch
                    }
                    // 无朗读内容的段落（只有标点/符号）：没有音频，但按顺序推进段落，
                    // 保证段落序号、高亮和"追加下一章"的时机不被打断
                    (keyParagraphMap[ds.key]?.firstOrNull()?.tag as? Int)?.let { index ->
                        paragraphChangeCallback?.invoke(index)
                    }
                    continue
                }
                val playedBytes = ds.synthesizedBytes
                playSegment(ds)
                // 播放消耗掉一段：从"领先量"里扣除，保证预加载按剩余播放时长计算
                aheadCount.updateAndGet { (it - 1).coerceAtLeast(0) }
                aheadBytes.updateAndGet { (it - playedBytes).coerceAtLeast(0L) }
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
                                    if (paragraphMode) {
                                        // 段落模式：立即回传段落序号（先于 player.start/onPlaybackStarted）。
                                        // 否则 onPlaybackStarted 先触发、onParagraphChanged 后触发，
                                        // 会把 playbackStarted 重置回 false，导致后续段进度卡在第一句
                                        notifyProgress(lines.first().tag)
                                    } else {
                                        startEstimateForLines(lines, if (player.duration > 0) player.duration else 0)
                                    }
                                } else {
                                    cancelEstimate()
                                }
                                // 段落模式按音频播放位置上报段内进度
                                if (paragraphMode) startSegmentProgressPoller(ds) else cancelSegmentProgress()
                            } catch (_: Throwable) {
                                cancelEstimate()
                                cancelSegmentProgress()
                            }
                            isSpeaking.postValue(true)
                            try {
                                player.start()
                                // 音频真正开始出声，通知调用方对齐时间模型起点
                                onPlaybackStarted?.invoke()
                            } catch (_: Throwable) {
                                cancelEstimate()
                                cancelSegmentProgress()
                                if (cont.isActive) cont.resume(Unit) {}
                            }
                        }

                        mp.setOnCompletionListener {
                            cancelEstimate()
                            cancelSegmentProgress()
                            if (cont.isActive) cont.resume(Unit) {}
                        }

                        mp.setOnErrorListener { _, _, _ ->
                            cancelEstimate()
                            cancelSegmentProgress()
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
                        cancelSegmentProgress()
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
        cancelSegmentProgress()
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
        synthQueue.clear()
        synthFinished = false
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

                // 合成并喂入数据；连接偶发失败时重试一次，避免整段被跳过
                var ok = false
                for (attempt in 0..1) {
                    if (stopped.get()) return@launch
                    try {
                        val input = edgeFetch.synthesizeText(text, rate, voice)
                        val buf = ByteArray(8 * 1024)
                        var read = input.read(buf)
                        while (read >= 0) {
                            if (stopped.get()) return@launch
                            if (read > 0) ds.feed(buf.copyOf(read))
                            read = input.read(buf)
                        }
                        if (ds.hasData) {
                            ok = true
                            break
                        }
                    } catch (e: Exception) {
                        Log.e("synthesis feed error: $key", e)
                    }
                }
                ds.markDone()
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

        synthQueue.clear()
        synthFinished = false
        aheadCount.set(0)
        aheadBytes.set(0)
        for (index in startIndex until paragraphs.size) {
            val text = paragraphs[index]
            if (text.isBlank()) continue
            synthQueue.add(listOf(TtsSpeakLine(text, index)))
        }
        // 整章都是空白：不再等待追加，直接按"读完"收尾（避免空等）
        if (synthQueue.isEmpty()) synthFinished = true

        // 缓存任务：从队列里顺序取段合成（队列可被 appendParagraphs 追加），写入 StreamingMediaDataSource
        cacheJob = GlobalScope.launch(Dispatchers.IO) {
            while (!stopped.get()) {
                // 预加载：至少领先 1 段，且领先音频总时长达到目标（默认 5 分钟）
                while (!stopped.get() && aheadCount.get() >= 1 && aheadBytes.get() >= preloadTargetBytes) {
                    kotlinx.coroutines.delay(50)
                }
                if (stopped.get()) return@launch
                val unit = synthQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (unit == null) {
                    // 队列暂时空：等"下一章"追加；确定不会再有就收尾
                    if (synthFinished) return@launch
                    continue
                }
                val text = unit.joinToString(separator = "") { line -> line.txt }
                val key = MD5Utils.md5Encode(text + "_" + rate + "_" + voice)
                keyParagraphMap[key] = unit

                val ds = StreamingMediaDataSource(key)
                playQueue.add(ds)

                // 纯标点/符号（如"……"）合成不出音频：不联网、不按失败处理，
                // 只推一个"空段"，由播放任务按顺序推进段落（否则会重试 3 次后误报网络失败）
                if (!TtsChapterSplitter.hasSpeakableText(text)) {
                    Log.i("TtsEdgeManager 跳过无朗读内容段落：${unit.firstOrNull()?.tag}")
                    ds.markDone()
                    continue
                }
                // 诊断：打印真正送进在线语音合成的文本（logcat 过滤 TTS_TEXT）
                TtsTextLogger.log(text, unit.firstOrNull()?.tag as? Int)

                // 内存缓存命中（暂停恢复/跳回同一段/重听）：不联网，直接喂给数据源立刻可播
                val cached = EdgeAudioCache.get(key)
                if (cached != null) {
                    ds.feed(cached)
                    ds.markDone()
                    aheadCount.incrementAndGet()
                    aheadBytes.addAndGet(cached.size.toLong())
                    continue
                }

                // 弱网语义：
                // - 预加载段失败：退避重试（3 次）；
                // - 播放追上时仍失败：这一段没有音频可播 → 标记失败，由播放任务停止会话并提示
                var ok = false
                var audioBytes: ByteArray? = null
                for (attempt in 0..2) {
                    if (stopped.get()) return@launch
                    val baos = java.io.ByteArrayOutputStream()
                    try {
                        val input = edgeFetch.synthesizeText(text, rate, voice)
                        val buf = ByteArray(8 * 1024)
                        var read = input.read(buf)
                        while (read >= 0) {
                            if (stopped.get()) return@launch
                            if (read > 0) {
                                val chunk = buf.copyOf(read)
                                ds.feed(chunk)
                                baos.write(chunk)
                            }
                            read = input.read(buf)
                        }
                        if (ds.hasData) {
                            ok = true
                            audioBytes = baos.toByteArray()
                            break
                        }
                    } catch (e: Exception) {
                        Log.e("synthesis feed error: $key", e)
                    }
                    if (attempt < 2) kotlinx.coroutines.delay(300L * (attempt + 1))
                }
                if (ok) {
                    ds.markDone()
                    // 只缓存成功合成的整段音频（失败/半截数据不缓存）
                    audioBytes?.let {
                        EdgeAudioCache.put(key, it)
                        aheadCount.incrementAndGet()
                        aheadBytes.addAndGet(it.size.toLong())
                    }
                } else {
                    ds.markFailed("网络合成失败（Edge TTS）")
                    Log.e("TtsEdgeManager 段落合成失败（重试用尽）：$key")
                }
            }
        }

        startPlayTask()
        return true
    }

    /**
     * 追加段落（顺序播放不重置）：把段落排到合成队列尾部，播放任务继续消费。
     *
     * 章节只存在于上层：这里只按"段落流"处理，不重建连接、不清播放队列。
     *
     * @param baseIndex 第一段在段落流里的扁平下标（上层负责编号，管理器只把它作为 tag 回传）
     */
    fun appendParagraphs(
        paragraphs: List<String>,
        baseIndex: Int,
        rate: Int,
        voice: String = "zh-CN-XiaoxiaoNeural",
    ) {
        if (paragraphs.isEmpty() || stopped.get()) return
        for (index in paragraphs.indices) {
            val text = paragraphs[index]
            if (text.isBlank()) continue
            // 纯标点/符号也放进来：合成时没有音频，但会按顺序推进段落（不能直接丢掉）
            synthQueue.add(listOf(TtsSpeakLine(text, baseIndex + index)))
        }
        Log.i("TTS追加 段数=${paragraphs.size} base=$baseIndex rate=$rate")
    }

    /** 测试用：调整预加载目标（毫秒），null 恢复默认 5 分钟 */
    internal fun configurePreloadForTest(ms: Long?) {
        preloadTargetMs = ms ?: MIN_PRELOAD_MS
    }

    /** 测试用：当前领先播放的（段数, 字节数） */
    internal fun aheadStatsForTest(): Pair<Int, Long> = aheadCount.get() to aheadBytes.get()

    /** 测试用：当前预加载目标（毫秒） */
    internal fun preloadTargetMsForTest(): Long = preloadTargetMs

    /** 不会再有追加的段落：合成队列排空后收尾（触发 onParagraphsCompleted） */
    fun finishParagraphs() {
        synthFinished = true
        Log.i("TTS追加 结束标记")
    }

    override suspend fun startParagraphs(paragraphs: List<String>, startIndex: Int): Boolean {
        return startParagraphs(paragraphs, startIndex, rate = 12, voice = "zh-CN-XiaoxiaoNeural")
    }

}
