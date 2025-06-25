package com.sjianjun.reader.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.QUEUE_ADD
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.sjianjun.reader.App
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.event.observe
import kotlinx.coroutines.*
import sjj.alog.Log
import sjj.novel.view.reader.page.TxtLine
import sjj.novel.view.reader.page.TxtPage
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.coroutines.resume

class TtsUtil(val context: Context, val lifecycle: Lifecycle) : LifecycleObserver {
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                textToSpeech?.shutdown()
                textToSpeech = null
            }
        })

//        监听耳机断开链接时间
        (context as BaseActivity).observe<String>(EventKey.ACTION_AUDIO_BECOMING_NOISY) {
            stop()
        }

    }

    private val paragraphs = ConcurrentLinkedDeque<List<TxtLine>>()

    lateinit var progressChangeCallback: (List<TxtLine>) -> Unit

    lateinit var onCompleted: () -> Unit

    private var textToSpeech: TextToSpeech? = null
    val isSpeaking: Boolean get() = textToSpeech?.isSpeaking == true || paragraphs.isNotEmpty()


    private suspend fun initTts(): TextToSpeech? {
        val tts = textToSpeech
        if (tts != null) {
            return tts
        }

        return suspendCancellableCoroutine { continuation ->
            Log.e("initTts")
            textToSpeech = TextToSpeech(context) { result ->
                Log.e("initTts: $result success:${result == TextToSpeech.SUCCESS}")
                if (result != TextToSpeech.SUCCESS) {
                    continuation.resume(null)
                    return@TextToSpeech
                }
                if (lifecycle.currentState >= Lifecycle.State.INITIALIZED) {
                    textToSpeech?.setOnUtteranceProgressListener(listener)
                    continuation.resume(textToSpeech)
                } else {
                    textToSpeech?.shutdown()
                    continuation.resume(null)
                }
            }


        }
    }


    suspend fun start(pages: List<TxtPage>?, pagePos: Int) {
        if (pages ==null || pages.isEmpty() || pagePos < 0 || pagePos >= pages.size) {
            return
        }
        if (lifecycle.currentState <= Lifecycle.State.DESTROYED) {
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
        if (lifecycle.currentState <= Lifecycle.State.DESTROYED) {
            return
        }
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

    companion object {
        init {
            App.app.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                        Log.e("ACTION_AUDIO_BECOMING_NOISY")
                        EventBus.post(EventKey.ACTION_AUDIO_BECOMING_NOISY)
                    }
                }

            }, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        }
    }

}