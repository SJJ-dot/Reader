package io.legado.app.service

import sjj.alog.Log

/**
 * Edge 合成音频的**内存**缓存（不落盘）。
 *
 * key = MD5(文本 + 语速 + 音色)，value = 该段完整 mp3 字节。
 *
 * 策略：
 * - **容量至少 5 分钟音频**：按 6 字节/毫秒折算约 1.8MB；实际取 可用内存/16，
 *   夹在 [8MB, 32MB]，即 8MB ≈ 23 分钟、32MB ≈ 93 分钟音频；
 * - **至少保留 1 段**：单段特别大（超过容量）时也只淘汰别的段、保留这段，
 *   绝不出现"一段都不缓存"；
 * - 淘汰按 LRU（最久未使用先走），命中会刷新使用顺序。
 *
 * 配合 [TtsEdgeManager] 的预加载：合成任务会提前合成并写入这里（至少 1 段、
 * 领先播放的音频总时长 ≥5 分钟），所以暂停恢复、来回跳章、重听同一段都能直接命中。
 */
object EdgeAudioCache {

    private const val TAG = "EdgeAudioCache"

    /** 与 TtsEdgeManager 的播放位置估算口径一致：6 字节 ≈ 1 毫秒音频 */
    const val BYTES_PER_MS = 6L

    /** 容量下限：至少缓存 5 分钟音频 */
    const val MIN_CAPACITY_MS = 5 * 60 * 1000L
    private const val MIN_CAPACITY_BYTES = MIN_CAPACITY_MS * BYTES_PER_MS

    /** 内存夹逼：下限 8MB（≈23 分钟音频），上限 32MB（≈93 分钟音频） */
    private const val MIN_CACHE_BYTES = 8L * 1024 * 1024
    private const val MAX_CACHE_BYTES = 32L * 1024 * 1024

    private val lock = Any()

    /** accessOrder = true：迭代顺序就是"最久未使用 → 最近使用" */
    private val entries = LinkedHashMap<String, ByteArray>(16, 0.75f, true)
    private var totalBytes = 0L
    private var hits = 0L
    private var misses = 0L

    /** 默认容量：可用内存的 1/16，且不低于 5 分钟、不小于 8MB、不大于 32MB */
    private val defaultMaxBytes: Int =
        maxOf(MIN_CAPACITY_BYTES, Runtime.getRuntime().maxMemory() / 16)
            .coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES)
            .toInt()

    @Volatile
    private var capacityOverride: Int? = null

    private val maxBytes: Int get() = capacityOverride ?: defaultMaxBytes

    /** 命中直接返回整段音频；未命中返回 null */
    fun get(key: String): ByteArray? = synchronized(lock) {
        val data = entries[key]
        if (data == null) {
            misses++
            null
        } else {
            hits++
            // accessOrder 已把它移到末尾（最近使用）
            data
        }
    }

    /** 合成完成后放入缓存（空数据不入；单段过大也保留，保证至少有一段） */
    fun put(key: String, data: ByteArray) {
        if (data.isEmpty()) return
        synchronized(lock) {
            entries.remove(key)?.let { totalBytes -= it.size }
            entries[key] = data
            totalBytes += data.size
            trim()
        }
        Log.i(
            "$TAG 缓存一段音频 key=$key size=${data.size} 总占用=${sizeBytes()} " +
                    "段数=${count()} 容量=${capacityBytes()}（约 ${capacityMinutes()} 分钟）"
        )
    }

    /** 超出容量时按 LRU 淘汰到容量以内；**任何情况下至少保留 1 段** */
    private fun trim() {
        val iterator = entries.entries.iterator()
        while (totalBytes > maxBytes && entries.size > 1) {
            val candidate = iterator.next()
            iterator.remove()
            totalBytes -= candidate.value.size
            Log.i("$TAG 淘汰一段音频 ${candidate.value.size} 字节，剩余 ${sizeBytes()}")
        }
    }

    fun clear() = synchronized(lock) {
        entries.clear()
        totalBytes = 0
    }

    fun sizeBytes(): Long = synchronized(lock) { totalBytes }

    fun count(): Int = synchronized(lock) { entries.size }

    fun hitCount(): Long = synchronized(lock) { hits }

    fun missCount(): Long = synchronized(lock) { misses }

    fun hitRate(): String = synchronized(lock) {
        val total = hits + misses
        if (total <= 0) "-" else "${hits * 100 / total}%"
    }

    /** 当前容量（字节），用于日志/测试 */
    fun capacityBytes(): Int = maxBytes

    /** 当前容量折算成音频分钟数（至少 5） */
    fun capacityMinutes(): Long = maxBytes / BYTES_PER_MS / 60_000

    /** 测试用：改容量（默认关闭），并清空已有数据；null 恢复默认 */
    internal fun configureForTest(capacityBytes: Int? = null) {
        synchronized(lock) {
            capacityOverride = capacityBytes
            entries.clear()
            totalBytes = 0
            hits = 0
            misses = 0
        }
    }
}
