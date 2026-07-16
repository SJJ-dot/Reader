package com.sjianjun.reader.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PerformanceMonitor {
    // 缓存正则表达式，避免重复创建
    private val spaceRegex = Regex("\\s+")
    private var lastCpuTime: Long = 0
    private var lastCpuJiffies: Long = 0

    var memMb: Int = 0
        private set
    var cpu: Int = 0
        private set

    // 在 IO 线程执行性能数据采集
    suspend fun getPerformanceStats(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        cpu = calculateCpuPercent()
        memMb = getLightweightMemory().toInt()
        cpu to memMb
    }

    private fun calculateCpuPercent(): Int {
        return try {
            val stat = java.io.File("/proc/self/stat").readText().trim()
            val fields = stat.substringAfterLast(')').split(spaceRegex)
            val utime = fields.getOrNull(11)?.toLongOrNull() ?: 0L
            val stime = fields.getOrNull(12)?.toLongOrNull() ?: 0L
            val jiffies = utime + stime
            val now = System.currentTimeMillis()

            val percent = if (lastCpuJiffies > 0 && lastCpuTime > 0) {
                val dtSec = (now - lastCpuTime) / 1000.0
                if (dtSec > 0) {
                    ((jiffies - lastCpuJiffies).toDouble() / 100.0 / dtSec * 100).toInt().coerceIn(0, 100)
                } else 0
            } else 0

            lastCpuJiffies = jiffies
            lastCpuTime = now
            percent
        } catch (e: Exception) {
            0
        }
    }

    private fun getLightweightMemory(): Long {
        return try {
            // 1. Java 堆已用内存
            val javaMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            // 2. Native 堆已用内存 (这个调用比 getMemoryInfo 轻量得多)
            val nativeMem = android.os.Debug.getNativeHeapAllocatedSize()

            (javaMem + nativeMem) / 1024 / 1024
        } catch (e: Exception) {
            0
        }
    }
}
