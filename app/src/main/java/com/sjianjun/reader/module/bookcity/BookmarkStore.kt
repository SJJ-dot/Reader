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

    /** 编辑收藏项：修改名称与地址，保持原位置与收藏时间 */
    fun update(oldUrl: String, title: String, url: String) {
        if (oldUrl.isBlank() || url.isBlank()) return
        val list = list()
        val idx = list.indexOfFirst { it.url == oldUrl }
        if (idx < 0) return
        val time = list[idx].time
        val before = list.subList(0, idx).filterNot { it.url == url }
        val after = list.subList(idx + 1, list.size).filterNot { it.url == url }
        save(before + listOf(Bookmark(title = title.ifBlank { url }, url = url, time = time)) + after)
    }

    /** 调整收藏顺序：把 from 位置的收藏移到 to 位置 */
    fun move(from: Int, to: Int) {
        if (from == to) return
        val list = list().toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val item = list.removeAt(from)
        list.add(to, item)
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
