package com.sjianjun.reader.event

import android.media.AudioManager

object EventKey {
    val CHAPTER_SYNC_FORCE = "CHAPTER_SYNC_FORCE"
    val CHAPTER_CONTENT_ERROR = "CHAPTER_CONTENT_ERROR"
    val CHAPTER_LIST = "CHAPTER_LIST"
    val CHAPTER_LIST_CAHE = "CHAPTER_LIST_CACHE"
    val CHAPTER_SPEAK = "CHAPTER_SPEAK"

    val ACTION_AUDIO_BECOMING_NOISY = AudioManager.ACTION_AUDIO_BECOMING_NOISY
}