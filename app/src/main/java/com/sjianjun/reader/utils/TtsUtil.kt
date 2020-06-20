package com.sjianjun.reader.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.QUEUE_ADD
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

class TtsUtil(val context: Context, val lifecycle: Lifecycle) : LifecycleObserver,
    CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Main) {
    init {
        lifecycle.addObserver(this)
    }

    val isSpeaking: Boolean
        get() = textToSpeech?.isSpeaking == true
    val isSpeakEnd :Boolean
        get() = contentParagraph.isEmpty()

    var progressChangeCallback: ((chapterIndex: Int, progress: Int, content: CharSequence?) -> Unit)? =
        null

    private var textToSpeech: TextToSpeech? = null

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    @Synchronized
    private suspend fun initTts(): TextToSpeech? {
        val tts = textToSpeech
        if (tts != null) {
            return tts
        }

        return suspendCancellableCoroutine { con ->

            val callback: (result: Int?, tts: TextToSpeech?) -> Unit = { result, tts ->
                if (result != null && tts != null) {
                    tts.setOnUtteranceProgressListener(listener)
                    if (result == TextToSpeech.SUCCESS && lifecycle.currentState >= Lifecycle.State.INITIALIZED) {
                        textToSpeech = tts
                        con.resume(tts) {
                            tts.shutdown()
                            textToSpeech = null
                        }
                    } else {
                        tts.shutdown()
                        con.resumeWithException(MessageException("Tts Init Failed"))
                    }
                }

            }

            var newTts: TextToSpeech? = null
            var result: Int? = null
            newTts = TextToSpeech(context) {
                result = it
                callback(result, newTts)
            }
            callback(result, newTts)

        }
    }


    private val contentParagraph = ConcurrentLinkedDeque<ContentParagraphBean>()
    suspend fun speak(chapterIndex: Int, content: CharSequence) {
        if (content.isBlank()) {
            return
        }
        initTts()
        contentParagraph.clear()
        contentParagraph.addAll(splitContentParagraph(chapterIndex, content))
        speak()
    }

    private fun speak() {
        if (lifecycle.currentState <= Lifecycle.State.DESTROYED) {
            return
        }
        val first = contentParagraph.firstOrNull() ?: return
        textToSpeech?.speak(
            first.paragraph,
            QUEUE_ADD,
            null,
            first.utteranceId
        )
    }

    private fun splitContentParagraph(
        chapterIndex: Int,
        content: CharSequence
    ): List<ContentParagraphBean> {
        var count = 0
        val contentId = content.md5
        val list = content.split("\n").mapNotNull { paragraph ->
            count += paragraph.length
            if (paragraph.isBlank()) {
                null
            } else {
                val bean = ContentParagraphBean(
                    chapterIndex,
                    paragraph,
                    contentId + "_" + paragraph.md5,
                    count - paragraph.length,
                    count
                )
                bean
            }


        }
        list.forEach {
            it.contentLength = count
        }
        return list
    }

    class ContentParagraphBean(
        val chapterIndex: Int,
        val paragraph: CharSequence,
        val utteranceId: String,
        val start: Int,
        val end: Int,
        var contentLength: Int = 0
    ) {
        val progress: Int
            get() = ((start + end).toFloat() * 50 / contentLength).roundToInt()

        val progressEnd: Int
            get() = (end.toFloat() * 100 / contentLength).roundToInt()
    }

    private val listener: UtteranceProgressListener by lazy {
        object : UtteranceProgressListener() {
            private var current: ContentParagraphBean? = null
            override fun onDone(utteranceId: String?) {
                contentParagraph.remove(current)
                current?.also {
                    progressChangeCallback?.invoke(
                        it.chapterIndex,
                        it.progressEnd,
                        current?.paragraph
                    )
                }
                speak()
            }

            override fun onError(utteranceId: String?) {
                launch {
                    toast("speak error")
                }
            }

            override fun onStart(utteranceId: String?) {
                current = contentParagraph.find { it.utteranceId == utteranceId }
                current?.also {
                    progressChangeCallback?.invoke(it.chapterIndex, it.progress, current?.paragraph)
                }
            }
        }
    }

}