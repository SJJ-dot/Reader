package com.sjianjun.reader.module.setting

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import android.webkit.WebStorage
import android.webkit.WebView
import com.bumptech.glide.Glide
import com.sjianjun.reader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sjj.alog.Log
import java.io.File

internal data class AppCacheStats(
    val imageBytes: Long,
    val webViewBytes: Long,
    val jsBytes: Long,
    val tempBytes: Long,
    /** 系统口径的缓存大小（StorageStats.cacheBytes，与系统"存储占用"页一致）；-1 表示获取失败 */
    val systemBytes: Long = -1L,
    /** 系统口径的用户数据大小（StorageStats.dataBytes，含数据库/文件/WebView数据等）；-1 表示获取失败 */
    val userDataBytes: Long = -1L,
) {
    val totalBytes: Long
        get() = imageBytes + webViewBytes + jsBytes + tempBytes

    /** 展示用的"当前占用"：优先系统口径，与系统设置里的"缓存"保持一致 */
    val displayBytes: Long
        get() = if (systemBytes >= 0) systemBytes else totalBytes
}

internal object SettingsCacheHelper {
    private val jsDownloadCacheName = Regex("^[0-9a-fA-F]{16}(\\.[A-Za-z0-9][A-Za-z0-9._-]{0,31})?$")

    suspend fun calculateStats(context: Context): AppCacheStats = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val imageFiles = imageCacheFiles(appContext)
        val webViewFiles = webViewCacheFiles(appContext)
        val jsFiles = jsCacheFiles(appContext) + jsDownloadCacheFiles(appContext)
        // 临时文件含 code_cache（系统口径把它计入"缓存"，统计与清理都覆盖）
        val tempFiles = otherCacheFiles(appContext, imageFiles + webViewFiles + jsFiles) + codeCacheFiles(appContext)
        val stats = AppCacheStats(
            imageBytes = imageFiles.sumOf { it.safeSize() },
            webViewBytes = webViewFiles.sumOf { it.safeSize() },
            jsBytes = jsFiles.sumOf { it.safeSize() },
            tempBytes = tempFiles.distinctByCanonicalPath().sumOf { it.safeSize() },
            systemBytes = systemCacheBytes(appContext),
            userDataBytes = systemDataBytes(appContext),
        )
        logCacheBreakdown(appContext, imageFiles, webViewFiles, jsFiles, tempFiles, stats)
        stats
    }

    suspend fun clearCaches(context: Context) {
        val appContext = context.applicationContext
        withContext(Dispatchers.Main) {
            runCatching { Glide.get(appContext).clearMemory() }
            runCatching { WebStorage.getInstance().deleteAllData() }
            runCatching {
                WebView(context).apply {
                    clearCache(true)
                    destroy()
                }
            }
        }
        withContext(Dispatchers.IO) {
            runCatching { Glide.get(appContext).clearDiskCache() }
            // 直接删除 legado-engine 的磁盘缓存目录；引擎存在时再反射清理其内存缓存
            runCatching { File(appContext.cacheDir, "ACache").deleteRecursively() }
            runCatching { File(appContext.cacheDir, "shareJs").deleteRecursively() }
            runCatching {
                Class.forName("io.legado.app.model.SharedJsScope").getMethod("clearCache").invoke(null)
            }
            // code_cache 同样计入系统"缓存"，一并清理（JIT 缓存系统会自动重建）
            runCatching { appContext.codeCacheDir.deleteRecursively() }
            jsDownloadCacheFiles(appContext).forEach { it.safeDelete() }
            otherCacheFiles(appContext, imageCacheFiles(appContext) + webViewCacheFiles(appContext) + jsCacheFiles(appContext) + jsDownloadCacheFiles(appContext))
                .forEach { it.clearContentsOrDeleteFile() }
        }
    }

    private fun imageCacheFiles(context: Context): List<File> {
        return listOf(glideDiskCacheDir(context)).distinctByCanonicalPath()
    }

    private fun glideDiskCacheDir(context: Context): File {
        return File(context.cacheDir, "image_manager_disk_cache")
    }

    private fun webViewCacheFiles(context: Context): List<File> {
        return listOf(
            File(context.cacheDir, "WebView"),
            File(context.cacheDir, "webview"),
            File(context.cacheDir, "webviewCache"),
            File(context.applicationInfo.dataDir, "app_webview"),
        ).distinctByCanonicalPath()
    }

    private fun jsCacheFiles(context: Context): List<File> {
        return listOf(
            File(context.cacheDir, "ACache"),
            File(context.cacheDir, "shareJs"),
        ).distinctByCanonicalPath()
    }

    private fun jsDownloadCacheFiles(context: Context): List<File> {
        val base = context.externalCacheDir ?: context.cacheDir
        return base.listFiles()
            ?.filter { it.isFile && jsDownloadCacheName.matches(it.name) }
            .orEmpty()
    }

    private fun otherCacheFiles(context: Context, knownFiles: List<File>): List<File> {
        val known = (knownFiles + browserReadModeCacheDir(context))
            .map { it.safeCanonicalPath() }
            .toSet()
        val files = mutableListOf<File>()
        cacheRoots(context).forEach { root ->
            root.listFiles()
                ?.filter { it.safeCanonicalPath() !in known }
                ?.let(files::addAll)
        }
        return files.distinctByCanonicalPath()
    }

    private fun browserReadModeCacheDir(context: Context): File {
        return File(context.cacheDir, "browser_read_mode")
    }

    private fun logCacheBreakdown(
        context: Context,
        imageFiles: List<File>,
        webViewFiles: List<File>,
        jsFiles: List<File>,
        tempFiles: List<File>,
        stats: AppCacheStats,
    ) {
        if (!BuildConfig.DEBUG) return
        Log.i("应用缓存统计 total=${stats.totalBytes} image=${stats.imageBytes} webView=${stats.webViewBytes} js=${stats.jsBytes} temp=${stats.tempBytes}")
        logCacheGroup("cacheDir", listOf(context.cacheDir))
        logCacheGroup("externalCacheDir", listOfNotNull(context.externalCacheDir))
        logCacheGroup("codeCacheDir", listOf(context.codeCacheDir))
        logCacheGroup("app_webview", listOf(File(context.applicationInfo.dataDir, "app_webview")))
        logCacheGroup("图片缓存", imageFiles)
        logCacheGroup("网页缓存", webViewFiles)
        logCacheGroup("阅读3.0规则缓存", jsFiles)
        logCacheGroup("临时文件", tempFiles)
        Log.i("阅读模式缓存 excluded ${browserReadModeCacheDir(context).safeCanonicalPath()} size=${browserReadModeCacheDir(context).safeSize()}")
    }

    private fun logCacheGroup(label: String, files: List<File>) {
        val existing = files.filter { it.exists() }
        val total = existing.sumOf { it.safeSize() }
        Log.i("$label total=$total count=${existing.size}")
        existing.forEach { file ->
            if (file.isDirectory) {
                file.listFiles()
                    ?.sortedByDescending { it.safeSize() }
                    ?.forEach { child ->
                        Log.i("$label child size=${child.safeSize()} path=${child.safeCanonicalPath()}")
                    }
            } else {
                Log.i("$label file size=${file.safeSize()} path=${file.safeCanonicalPath()}")
            }
        }
    }

    /** 系统口径的缓存大小：与系统设置-存储占用页显示的"缓存"一致（含 code_cache 与外部缓存） */
    private fun systemCacheBytes(context: Context): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return -1L
        return runCatching {
            val statsManager =
                context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
            statsManager?.queryStatsForUid(StorageManager.UUID_DEFAULT, context.applicationInfo.uid)
                ?.cacheBytes ?: -1L
        }.getOrDefault(-1L)
    }

    private fun codeCacheFiles(context: Context): List<File> = listOf(context.codeCacheDir)

    /** 系统口径的用户数据大小（与系统"存储占用"页的"用户数据"一致：数据库、files、WebView 数据等） */
    private fun systemDataBytes(context: Context): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return -1L
        return runCatching {
            val statsManager =
                context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
            statsManager?.queryStatsForUid(StorageManager.UUID_DEFAULT, context.applicationInfo.uid)
                ?.dataBytes ?: -1L
        }.getOrDefault(-1L)
    }

    private fun cacheRoots(context: Context): List<File> {
        return listOfNotNull(context.cacheDir, context.externalCacheDir).distinctByCanonicalPath()
    }

    private fun File.safeCanonicalPath(): String {
        return runCatching { canonicalPath }.getOrDefault(absolutePath)
    }

    private fun List<File>.distinctByCanonicalPath(): List<File> {
        return distinctBy { it.safeCanonicalPath() }
    }

    private fun File.safeSize(): Long {
        return runCatching {
            when {
                !exists() -> 0L
                isFile -> length()
                else -> listFiles()?.sumOf { it.safeSize() } ?: 0L
            }
        }.getOrDefault(0L)
    }

    private fun File.clearContentsOrDeleteFile() {
        if (!exists()) return
        if (isFile) {
            safeDelete()
        } else {
            listFiles()?.forEach { it.safeDeleteRecursively() }
        }
    }

    private fun File.safeDeleteRecursively() {
        runCatching { deleteRecursively() }
    }

    private fun File.safeDelete() {
        runCatching { delete() }
    }
}
