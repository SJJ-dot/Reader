package com.sjianjun.reader.module.setting

import android.content.Context
import android.webkit.WebStorage
import android.webkit.WebView
import com.bumptech.glide.Glide
import com.sjianjun.reader.BuildConfig
import io.legado.app.model.SharedJsScope
import io.legado.app.utils.ACache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sjj.alog.Log
import java.io.File

internal data class AppCacheStats(
    val imageBytes: Long,
    val webViewBytes: Long,
    val jsBytes: Long,
    val tempBytes: Long,
) {
    val totalBytes: Long
        get() = imageBytes + webViewBytes + jsBytes + tempBytes
}

internal object SettingsCacheHelper {
    private val jsDownloadCacheName = Regex("^[0-9a-fA-F]{16}(\\.[A-Za-z0-9][A-Za-z0-9._-]{0,31})?$")

    suspend fun calculateStats(context: Context): AppCacheStats = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val imageFiles = imageCacheFiles(appContext)
        val webViewFiles = webViewCacheFiles(appContext)
        val jsFiles = jsCacheFiles(appContext) + jsDownloadCacheFiles(appContext)
        val tempFiles = otherCacheFiles(appContext, imageFiles + webViewFiles + jsFiles)
        val stats = AppCacheStats(
            imageBytes = imageFiles.sumOf { it.safeSize() },
            webViewBytes = webViewFiles.sumOf { it.safeSize() },
            jsBytes = jsFiles.sumOf { it.safeSize() },
            tempBytes = tempFiles.sumOf { it.safeSize() },
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
            runCatching { ACache.get().clear() }
            runCatching { SharedJsScope.clearCache() }
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
