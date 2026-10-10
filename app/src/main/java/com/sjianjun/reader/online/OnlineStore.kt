package com.sjianjun.reader.online

import android.content.Context
import android.content.SharedPreferences
import com.sjianjun.reader.App

/**
 * 在线时长的本地账本（待上传记录）。
 *
 * 以「日期 → 秒数」分桶而不是只存一个总数：排行榜有 日/周/月/年 维度，
 * 服务端要按天归集，本地就必须把日期一起带上。
 *
 * 记录只在服务端确认收到后才扣减（见 [OnlineRecorder]），所以断网、崩溃都不会丢时长。
 */
object OnlineStore {

    private const val PREF_NAME = "online_stats"
    private const val KEY_PENDING = "pending"

    private val lock = Any()

    private val pref: SharedPreferences
        get() = App.app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** 今天的日期（yyyy-MM-dd），按本机时区分桶 */
    fun today(): String = java.time.LocalDate.now().toString()

    /** 累加若干秒到指定日期 */
    fun add(seconds: Long, day: String = today()) {
        if (seconds <= 0) return
        synchronized(lock) {
            val pending = readLocked()
            pending[day] = (pending[day] ?: 0L) + seconds
            writeLocked(pending)
        }
    }

    /** 当前全部待上传记录（日期 → 秒数），按日期升序 */
    fun snapshot(): List<DailySeconds> = synchronized(lock) {
        readLocked().map { DailySeconds(it.key, it.value) }
    }

    /**
     * 扣掉已成功上传的部分。
     *
     * 上传期间可能又有新的秒数入账，所以不能直接清空，只能拿上传时的快照逐条相减。
     */
    fun subtract(uploaded: List<DailySeconds>) = synchronized(lock) {
        if (uploaded.isEmpty()) return@synchronized
        val pending = readLocked().toMutableMap()
        uploaded.forEach { item ->
            val rest = (pending[item.day] ?: 0L) - item.seconds
            if (rest > 0) pending[item.day] = rest else pending.remove(item.day)
        }
        writeLocked(pending)
    }

    /** 存储格式：`2026-10-10:123;2026-10-11:45` */
    private fun readLocked(): MutableMap<String, Long> {
        val raw = pref.getString(KEY_PENDING, null) ?: return mutableMapOf()
        val result = mutableMapOf<String, Long>()
        raw.split(';').forEach { entry ->
            if (entry.isEmpty()) return@forEach
            val index = entry.lastIndexOf(':')
            if (index <= 0) return@forEach
            val day = entry.substring(0, index)
            val seconds = entry.substring(index + 1).toLongOrNull() ?: return@forEach
            if (seconds > 0) result[day] = seconds
        }
        return result
    }

    private fun writeLocked(pending: Map<String, Long>) {
        val raw = pending.toSortedMap().entries.joinToString(";") { "${it.key}:${it.value}" }
        pref.edit().putString(KEY_PENDING, raw).apply()
    }
}

/** 某一天的在线秒数 */
class DailySeconds(val day: String, val seconds: Long)
