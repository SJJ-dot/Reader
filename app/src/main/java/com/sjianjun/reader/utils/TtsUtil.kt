package com.sjianjun.reader.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.QUEUE_ADD
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import com.sjianjun.reader.App
import kotlinx.coroutines.*
import sjj.alog.Log
import sjj.novel.view.reader.page.TxtLine
import sjj.novel.view.reader.page.TxtPage
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.coroutines.resume

class TtsUtil() : ViewModel() {


    private val paragraphs = ConcurrentLinkedDeque<List<TxtLine>>()

    lateinit var progressChangeCallback: (List<TxtLine>) -> Unit

    lateinit var onCompleted: () -> Unit

    private var textToSpeech: TextToSpeech? = null
    val isSpeaking: Boolean get() = textToSpeech?.isSpeaking == true || paragraphs.isNotEmpty()
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


        this.paragraphs.clear()
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

        textToSpeech?.stop()
        speakNext()
    }

    fun stop() {
        textToSpeech?.stop()
        paragraphs.clear()
    }


    private fun speakNext() {
        GlobalScope.launch(Dispatchers.Main) {
            delay(100)
            val txtLines = paragraphs.peek()?.also {
                val txt = it.joinToString(separator = "") { line -> line.txt }
                textToSpeech?.speak(
                    txt,
                    QUEUE_ADD,
                    null,
                    txt.md5
                )
            }
            if (txtLines == null) {
                onCompleted.invoke()
            }
        }
    }

    private val listener: UtteranceProgressListener by lazy {
        object : UtteranceProgressListener() {
            override fun onDone(utteranceId: String?) {
                paragraphs.poll()?.also {
                    progressChangeCallback.invoke(it)
                }
                speakNext()
            }

            override fun onError(utteranceId: String?) {
                paragraphs.poll()
                toast("speak error")
            }

            override fun onStart(utteranceId: String?) {
                paragraphs.peek()?.let { progressChangeCallback.invoke(it) }
            }
        }
    }


}