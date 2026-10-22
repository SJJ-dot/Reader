package com.sjianjun.reader.module.bookcity

import com.google.gson.Gson
import com.tencent.mmkv.MMKV

/** 浏览器收藏夹（本地 MMKV 存储） */
object BookmarkStore {

    data class Bookmark(
        val title: String,
        val url: String,
        val time: Long = System.currentTimeMillis(),
    )

    private val mmkv = MMKV.mmkvWithID("BrowserBookmarks")
    private val gson = Gson()

    fun list(): List<Bookmark> = runCatching {
        val json = mmkv.decodeString("list", "[]") ?: "[]"
        val array = gson.fromJson(json, Array<Bookmark>::class.java)
        array?.toList() ?: emptyList()
    }.getOrDefault(emptyList())

    private fun save(list: List<Bookmark>) {
        mmkv.encode("list", gson.toJson(list))
    }

    fun add(title: String, url: String) {
        if (url.isBlank()) return
        val list = list().filterNot { it.url == url }.toMutableList()
        list.add(0, Bookmark(title = title.ifBlank { url }, url = url))
        save(list)
    }

    fun remove(url: String) {
        save(list().filterNot { it.url == url })
    }

    fun clear() {
        save(emptyList())
    }

    fun contains(url: String): Boolean = list().any { it.url == url }
}
