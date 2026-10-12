package com.sjianjun.reader.module.feedback

import android.content.Context
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sjj.alog.Log
import java.io.File

/**
 * 反馈页资源包（index.html / styles.css / app.js）。
 *
 * 页面本体内置在安装包里（assets/feedback），打开即渲染、不联网、断网也能看；
 * 这里负责在后台比对线上版本号，有新版就下载到私有目录作为「覆盖包」，
 * 下次打开优先用覆盖包 —— 于是改反馈页不必再发 APP 版本。
 *
 * 版本号由服务端对三个文件算哈希得出，改任一文件都会变，不需要手工维护。
 * 任何一步失败都静默放弃、继续用旧版本，绝不会因此打不开反馈页。
 */
object FeedbackBundle {

    /** 安装包内的目录名 */
    private const val ASSET_DIR = "feedback"

    /** 覆盖包目录名（放在 filesDir 下） */
    private const val DIR_NAME = "feedback_h5"

    private const val VERSION_FILE = "version.txt"

    /** 需要下载与校验的文件，与服务端 H5_FILES 保持一致 */
    val FILES = listOf("index.html", "styles.css", "app.js")

    /** 页面入口文件名 */
    const val ENTRY = "index.html"

    /** 覆盖包目录（可能不存在） */
    fun dir(context: Context): File = File(context.filesDir, DIR_NAME)

    /** 读一个资源文件：覆盖包优先、安装包兜底；都没有返回 null */
    fun read(context: Context, name: String): ByteArray? {
        val overlay = File(dir(context), name)
        if (overlay.isFile) runCatching { return overlay.readBytes() }
        return runCatching {
            context.assets.open("$ASSET_DIR/$name").use { it.readBytes() }
        }.getOrNull()
    }

    /** 本地当前版本：覆盖包 > 安装包内置，都读不到则为空串 */
    fun localVersion(context: Context): String {
        val overlay = File(dir(context), VERSION_FILE)
        if (overlay.isFile) runCatching { return overlay.readText().trim() }
        return runCatching {
            context.assets.open("$ASSET_DIR/$VERSION_FILE").use { String(it.readBytes(), Charsets.UTF_8).trim() }
        }.getOrDefault("")
    }

    /**
     * 后台同步：先比版本，不同才下载整包，下载校验通过后原子替换覆盖包。
     *
     * 原子性做法：先把整包写进临时目录，全部写完再把旧覆盖包挪走、临时目录改名顶上；
     * 改名失败就把旧的挪回来。因此不会出现「半新半旧」的坏页面。
     */
    suspend fun sync(context: Context) = withContext(Dispatchers.IO) {
        val remote = FeedbackApi.remoteVersion() ?: return@withContext
        val version = gson.fromJson<Map<String, String>>(remote)?.get("v")?.trim().orEmpty()
        if (version.isEmpty() || version == localVersion(context)) return@withContext

        val raw = FeedbackApi.remoteBundle() ?: return@withContext
        val payload = gson.fromJson<BundlePayload>(raw) ?: return@withContext
        val files = payload.files ?: return@withContext
        // 版本对不上、或文件缺失，都当作这次没准备好，下次再试
        if (payload.v != version || FILES.any { files[it].isNullOrEmpty() }) return@withContext

        val tmp = File(context.filesDir, "$DIR_NAME.tmp")
        val live = dir(context)
        val bak = File(context.filesDir, "$DIR_NAME.bak")
        try {
            tmp.deleteRecursively()
            if (!tmp.mkdirs()) return@withContext
            FILES.forEach { name -> File(tmp, name).writeText(files.getValue(name)) }
            File(tmp, VERSION_FILE).writeText(version)

            bak.deleteRecursively()
            if (live.exists() && !live.renameTo(bak)) return@withContext
            if (!tmp.renameTo(live)) {
                bak.renameTo(live)      // 换包失败：回滚，保证还有页面可用
                return@withContext
            }
            bak.deleteRecursively()
            Log.i("反馈页已更新到 $version")
        } catch (e: Exception) {
            Log.e("反馈页热更新失败: ${e.message}")
        } finally {
            tmp.deleteRecursively()
        }
    }

    private data class BundlePayload(val v: String?, val files: Map<String, String>?)
}
