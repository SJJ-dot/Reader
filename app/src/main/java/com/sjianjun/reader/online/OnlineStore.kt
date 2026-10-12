package com.sjianjun.reader.online

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.annotations.SerializedName
import com.sjianjun.reader.App
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 在线时长的本地账本（待上传记录）。
 *
 * 以「小时 → 秒数」分桶，而不是只存一个总数：排行榜的 24H / 周 / 月 / 年 都是**滑动窗口**
 * （最近 24 小时 / 最近 7 天 / 最近 30 天 / 最近 365 天），服务端只有拿到小时粒度的数据
 * 才能算准，所以本地也必须把时间一起带上。
 *
 * 记录只在服务端确认收到后才扣减（见 [OnlineRecorder]），所以断网、崩溃都不会丢时长。
 */
object OnlineStore {

    private const val PREF_NAME = "online_stats"
    private const val KEY_PENDING = "pending"

    /** 桶名格式，与云端 `::timestamp` 能解析的格式一致 */
    private val BUCKET_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    private val lock = Any()

    private val pref: SharedPreferences
        get() = App.app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** 当前所在的小时桶（yyyy-MM-ddTHH:mm:ss），按本机时区划分 */
    fun currentBucket(): String = LocalDateTime.now()
        .withMinute(0).withSecond(0).withNano(0)
        .format(BUCKET_FORMAT)

    /** 累加若干秒到指定小时桶 */
    fun add(seconds: Long, bucket: String = currentBucket()) {
        if (seconds <= 0) return
        synchronized(lock) {
            val pending = readLocked()
            pending[bucket] = (pending[bucket] ?: 0L) + seconds
            writeLocked(pending)
        }
    }

    /** 当前全部待上传记录（小时 → 秒数），按时间升序 */
    fun snapshot(): List<TimeBucket> = synchronized(lock) {
        readLocked().map { TimeBucket(it.key, it.value) }
    }

    /**
     * 扣掉已成功上传的部分。
     *
     * 上传期间可能又有新的秒数入账，所以不能直接清空，只能拿上传时的快照逐条相减。
     */
    fun subtract(uploaded: List<TimeBucket>) = synchronized(lock) {
        if (uploaded.isEmpty()) return@synchronized
        val pending = readLocked().toMutableMap()
        uploaded.forEach { item ->
            val rest = (pending[item.bucket] ?: 0L) - item.seconds
            if (rest > 0) pending[item.bucket] = rest else pending.remove(item.bucket)
        }
        writeLocked(pending)
    }

    /** 存储格式：`2026-10-12T09:00:00:600;2026-10-12T10:00:00:45` */
    private fun readLocked(): MutableMap<String, Long> {
        val raw = pref.getString(KEY_PENDING, null) ?: return mutableMapOf()
        val result = mutableMapOf<String, Long>()
        raw.split(';').forEach { entry ->
            if (entry.isEmpty()) return@forEach
            // 桶名本身带冒号，所以从最后一个冒号处切分
            val index = entry.lastIndexOf(':')
            if (index <= 0) return@forEach
            val bucket = entry.substring(0, index)
            val seconds = entry.substring(index + 1).toLongOrNull() ?: return@forEach
            if (seconds > 0) result[bucket] = seconds
        }
        return result
    }

    private fun writeLocked(pending: Map<String, Long>) {
        val raw = pending.toSortedMap().entries.joinToString(";") { "${it.key}:${it.value}" }
        pref.edit().putString(KEY_PENDING, raw).apply()
    }
}

/**
 * 某个小时桶的在线秒数。
 *
 * [bucket] 形如 `2026-10-12T09:00:00`（本机时区），上报时序列化为 `t` 字段。
 * 老版本账本里存的是日期（`2026-10-12`），服务端会把它当作当天 00:00 入账，不必迁移。
 */
class TimeBucket(
    @SerializedName("t") val bucket: String,
    val seconds: Long
)
