package com.sjianjun.reader.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.QUEUE_ADD
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.sjianjun.reader.App
import kotlinx.coroutines.*
import sjj.alog.Log
import sjj.novel.view.reader.page.TxtLine
import sjj.novel.view.reader.page.TxtPage
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.coroutines.resume
import kotlin.math.abs

class TtsUtil() : ViewModel() {


    private val paragraphs = ConcurrentLinkedDeque<List<TxtLine>>()

    lateinit var progressChangeCallback: (TxtLine) -> Unit

    lateinit var onCompleted: () -> Unit

    private var textToSpeech: TextToSpeech? = null
    val isSpeaking = MutableLiveData(false)
    private var speakTime = 0L
    private var estimateJob: Job? = null
    private var baseMsPerWeight: Long = 200L
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.e("ACTION_AUDIO_BECOMING_NOISY")
                stop()
            }
        }

    }

    init {
        App.app.registerReceiver(broadcastReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }


    override fun onCleared() {
        stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        App.app.unregisterReceiver(broadcastReceiver)
    }

    private suspend fun initTts(): TextToSpeech? {
        val tts = textToSpeech
        if (tts != null) {
            return tts
        }

        return suspendCancellableCoroutine { continuation ->
            Log.e("initTts")
            textToSpeech = TextToSpeech(App.app) { result ->
                Log.e("initTts: $result success:${result == TextToSpeech.SUCCESS}")
                if (result != TextToSpeech.SUCCESS) {
                    continuation.resume(null)
                    return@TextToSpeech
                }
                textToSpeech?.setOnUtteranceProgressListener(listener)
                if (continuation.isActive) {
                    continuation.resume(textToSpeech)
                } else {
                    textToSpeech?.shutdown()
                    textToSpeech = null
                    continuation.resume(null)
                }
            }


        }
    }


    suspend fun start(pages: List<TxtPage>?, pagePos: Int) {
        if (pages.isNullOrEmpty() || pagePos < 0 || pagePos >= pages.size) {
            return
        }
        if (initTts() == null) {
            toast("语音引擎初始化失败")
            return
        }
        stop()
        isSpeaking.postValue(true)
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
                            this.paragraphs.add(paragraphLines)
                            paragraphLines = mutableListOf()
                        }
                    }

                    paragraphLines.add(line)
                    if (line.isParaEnd) {
                        if (paragraphLines.isNotEmpty()) {
                            this.paragraphs.add(paragraphLines)
                            paragraphLines = mutableListOf()
                        }
                    }
                }
            }
        }
        if (paragraphLines.isNotEmpty()) {
            this.paragraphs.add(paragraphLines)
        }
        speakNext()
    }

    fun stop() {
        textToSpeech?.stop()
        paragraphs.clear()
        // cancel any running estimator
        estimateJob?.cancel()
        estimateJob = null
        if (isSpeaking.value != false) {
            isSpeaking.postValue(false)
        }
    }


    private fun speakNext() {
        GlobalScope.launch(Dispatchers.Main) {
            delay(100)
            val paragraph = paragraphs.peek()
            if (paragraph == null) {
                isSpeaking.value = false
                onCompleted.invoke()
                return@launch
            }
            val txt = paragraph.joinToString(separator = "") { line -> line.txt }
            textToSpeech?.speak(txt, QUEUE_ADD, null, txt.md5)
        }
    }

    // start a coroutine to estimate playback percentage for the given paragraph text
    private fun startEstimateForParagraph() {
        val paragraph = paragraphs.peek() ?: return
        // build simple weight model per character
        val expectedLineMs = mutableListOf<Pair<Long, TxtLine> >()
        for (line in paragraph) {
            var lineWeight = 0f
            line.txt.forEach {
                lineWeight += when (it) {
                    ',', '，', '、' -> 2f
                    '.', '。', '!', '！', '?', '？', ';', '；' -> 2.2f
                    ' ', '\t', '\n' -> 0.6f
                    else -> 1.0f
                }
            }
            expectedLineMs.add((lineWeight * baseMsPerWeight).toLong() to line)
        }
        val startTime = System.currentTimeMillis()
        var lastTextLine: TxtLine = paragraph.first()
        estimateJob?.cancel()
        estimateJob = GlobalScope.launch(Dispatchers.Main) {
            try {
                while (isActive) {
                    if (paragraphs.peek() != paragraph) {
                        val expectedTotalTime = expectedLineMs.sumOf { it.first }
                        if (speakTime > 5000 && abs(speakTime - expectedTotalTime) > 1000) {
                            baseMsPerWeight = (baseMsPerWeight.toFloat() * speakTime / expectedTotalTime).toLong()
                            Log.i("Adjusting baseMsPerWeight to $baseMsPerWeight ms per weight unit, speakTime: $speakTime ms, expectedTotalTime: $expectedTotalTime ms")
                        }
                        break
                    }
                    val elapsed = System.currentTimeMillis() - startTime
                    var elapsedLine = 0L
                    for ((time,line) in expectedLineMs) {
                        elapsedLine += time
                        if (elapsed < elapsedLine) {
                            if (line != lastTextLine) {
                                progressChangeCallback.invoke(line)
                                lastTextLine = line
                            }
                            break
                        }
                    }
                    delay(150)
                }
                val actualElapsedTime = System.currentTimeMillis() - startTime
                val expectedTotalMs = expectedLineMs.sumOf { it.first }
                Log.i("actualElapsedTime: $actualElapsedTime ms, ExpectedTime: $expectedTotalMs ms, dif:${expectedTotalMs - actualElapsedTime}")
            } finally {
                // ensure cleanup but keep polling/Done listener to advance queue
            }
        }
    }

    private val listener: UtteranceProgressListener by lazy {
        var speakStartTime = 0L
        object : UtteranceProgressListener() {
            override fun onDone(utteranceId: String?) {
                speakTime = System.currentTimeMillis() - speakStartTime
                paragraphs.poll()?.let {
                    progressChangeCallback.invoke(it.last())
                }
                speakNext()
            }

            override fun onError(utteranceId: String?) {
                stop()
                toast("speak error")
            }

            override fun onStart(utteranceId: String?) {
                paragraphs.peek()?.let {
                    progressChangeCallback.invoke(it.first())
                }
                startEstimateForParagraph()
                speakStartTime = System.currentTimeMillis()
            }
        }
    }


}