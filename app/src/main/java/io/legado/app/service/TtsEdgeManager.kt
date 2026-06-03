package io.legado.app.service

import android.media.MediaPlayer
import android.os.Build
import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.App
import io.legado.app.utils.MD5Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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
object TtsEdgeManager {
    private val audioCache = HashMap<String, ByteArray>()
    private val audioKeys = CopyOnWriteArrayList<String>()
    // mapping from cached key to paragraph lines (used to notify progress)
    private val keyParagraphMap = HashMap<String, List<TxtLine>>()
    private var mediaPlayer: MediaPlayer? = null
    private val playQueue = ConcurrentLinkedDeque<String>()
    private var currentKey: String? = null
    var playbackRate = 1.0f

    // Compatibility with TtsUtil usage
    val isSpeaking = MutableLiveData(false)
    lateinit var progressChangeCallback: (TxtLine) -> Unit
    lateinit var onCompleted: () -> Unit

    fun cacheAudio(key: String, bytes: ByteArray) {
        audioCache[key] = bytes
        if (!audioKeys.contains(key)) audioKeys.add(key)
    }

    /**
     * Synthesize the given text using EdgeSpeakFetch and cache the resulting mp3 bytes.
     * Returns the cache key (md5 of text+rate).
     */
    fun synthesizeAndCache(text: String, rate: Int, voice: String = "zh-CN-XiaoxiaoNeural", paragraphLines: List<TxtLine>? = null): String {
        val key = MD5Utils.md5Encode(text + "_" + rate + "_" + voice)
        if (isCached(key)) {
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
            val bytes = baos.toByteArray()
            cacheAudio(key, bytes)
            paragraphLines?.let { keyParagraphMap[key] = it }
        } catch (e: Exception) {
            // synthesis failed; leave uncached
        }
        return key
    }

    fun isCached(key: String): Boolean = audioCache.containsKey(key)

    fun removeCache(key: String) {
        audioCache.remove(key)
        audioKeys.remove(key)
        // remove any temp file if exists
        try {
            val f = File(App.app.cacheDir, "tts_$key.mp3")
            if (f.exists()) f.delete()
        } catch (_: Throwable) {}
    }

    fun removeAllCache() {
        audioCache.clear()
        audioKeys.clear()
        // clear temp files
        try {
            App.app.cacheDir.listFiles()?.forEach { f -> if (f.name.startsWith("tts_") ) f.delete() }
        } catch (_: Throwable) {}
    }

    fun playKeys(keys: List<String>) {
        playQueue.clear()
        playQueue.addAll(keys)
        playNext()
    }

    private fun playNext() {
        val key = playQueue.pollFirst() ?: run {
            // finished
            stop()
            try {
                isSpeaking.postValue(false)
                onCompleted.invoke()
            } catch (_: Exception) {}
            return
        }
        playKey(key)
    }

    private fun playKey(key: String) {
        currentKey = key
        val bytes = audioCache[key]
        if (bytes == null) {
            playNext()
            return
        }
        try {
            val tmp = File(App.app.cacheDir, "tts_$key.mp3")
            tmp.writeBytes(bytes)
            mediaPlayer?.reset()
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(tmp.absolutePath)
                setOnPreparedListener { mp ->
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            mp.playbackParams = mp.playbackParams.setSpeed(playbackRate)
                        }
                    } catch (_: Throwable) {}
                    // notify progress for the paragraph associated with this key
                    try {
                        keyParagraphMap[key]?.let { lines ->
                            if (lines.isNotEmpty()) progressChangeCallback.invoke(lines.first())
                        }
                    } catch (_: Throwable) {}
                    isSpeaking.postValue(true)
                    mp.start()
                }
                setOnCompletionListener { playNext() }
                setOnErrorListener { _, _, _ -> playNext(); true }
                prepareAsync()
            }
        } catch (e: IOException) {
            // skip and play next
            playNext()
        }
    }

    fun pause() {
        mediaPlayer?.let { if (it.isPlaying) it.pause() }
    }

    fun resume() {
        mediaPlayer?.let { if (!it.isPlaying) it.start() }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
        } catch (_: Throwable) {}
        try { mediaPlayer?.release() } catch (_: Throwable) {}
        mediaPlayer = null
        playQueue.clear()
        currentKey = null
    }

    /**
     * Start speaking pages from pagePos using Edge TTS.
     * This will synthesize paragraphs in background and enqueue them for playback.
     */
    fun start(pages: List<TxtPage>?, pagePos: Int, rate: Int = 12, voice: String = "zh-CN-XiaoxiaoNeural") {
        if (pages.isNullOrEmpty() || pagePos < 0 || pagePos >= pages.size) return
        stop()
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

        // synthesize and enqueue sequentially. Start playback as soon as the first paragraph is ready.
        GlobalScope.launch(Dispatchers.IO) {
            try {
                for (para in paragraphs) {
                    val text = para.joinToString(separator = "") { line -> line.txt }
                    val key = synthesizeAndCache(text, rate, voice, para)
                    // enqueue for playback
                    playQueue.add(key)
                    // if nothing is playing yet, start
                    if (mediaPlayer == null) playNext()
                }
            } catch (_: Exception) {
            }
        }
    }

    // helper to play a sequence built from cached keys automatically
    fun playAllCachedFrom(key: String) {
        val startIndex = audioKeys.indexOf(key).coerceAtLeast(0)
        val keys = audioKeys.subList(startIndex, audioKeys.size)
        playKeys(keys)
    }

}
