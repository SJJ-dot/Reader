package com.sjianjun.reader.utils

import android.os.Debug
import android.os.Handler
import android.os.Looper
import sjj.alog.Log
import java.io.File

/**
 * APP CPU/内存消耗监控。单例，非 Android Service 组件。
 * 通过 [start] / [stop] 控制，每 5 秒采样一次，数据保留在内存环形缓冲（最多 360 条 ≈ 30 分钟）。
 */
object PerfMonitorService {
    private const val INTERVAL_MS = 1000L
    private const val MAX_SAMPLES = 360

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var running = false

    /** 上一次采样的 jiffies（utime+stime）和墙钟时间，用于 CPU 差值计算 */
    private var lastCpuJiffies: Long = 0
    private var lastWallTime: Long = 0

    fun start() {
        if (running) return
        running = true
        lastCpuJiffies = readProcessCpuJiffies()
        lastWallTime = System.currentTimeMillis()
        handler.post(samplingRunnable)
        Log.i("PerfMonitorService started")
    }

    fun stop() {
        running = false
        handler.removeCallbacks(samplingRunnable)
        Log.i("PerfMonitorService stopped")
    }

    private val samplingRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            sample()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    private fun sample() {
        val now = System.currentTimeMillis()
        val cpuJiffies = readProcessCpuJiffies()
        // CPU% = Δ(jiffies) / clkTck / Δ(seconds) * 100
        val clkTck = 100L // sysconf(_SC_CLK_TCK)，Android 固定 100
        val dtSec = (now - lastWallTime) / 1000.0
        val cpuPercent = if (dtSec > 0 && lastCpuJiffies > 0) {
            ((cpuJiffies - lastCpuJiffies).toDouble() / clkTck / dtSec * 100).toFloat().coerceAtLeast(0f)
        } else {
            0f
        }
        lastCpuJiffies = cpuJiffies
        lastWallTime = now

        // 内存：totalPrivateDirty (KB)
        val memKb = Debug.MemoryInfo().let { info ->
            Debug.getMemoryInfo(info)
            info.totalPrivateDirty.toLong()
        }

        Log.i("PerfMonitorService sample: CPU=${"%.2f".format(cpuPercent)}%, Memory=${memKb / 1024L}MB")
    }

    /**
     * 读取 /proc/self/stat 的 utime(14) + stime(15)，单位 jiffies。
     * 失败返回 0。
     */
    private fun readProcessCpuJiffies(): Long {
        return try {
            val stat = File("/proc/self/stat").readText().trim()
            val fields = stat.substringAfterLast(')').split(Regex("\\s+"))
            // fields[0]=utime, fields[1]=stime（去掉 comm 字段后的第 14、15 项）
            val utime = fields.getOrElse(11) { "0" }.toLong()
            val stime = fields.getOrElse(12) { "0" }.toLong()
            utime + stime
        } catch (e: Exception) {
            Log.e("readProcessCpuJiffies err", e)
            0L
        }
    }
}
