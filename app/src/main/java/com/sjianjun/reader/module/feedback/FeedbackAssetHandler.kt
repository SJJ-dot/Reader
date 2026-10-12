package com.sjianjun.reader.module.feedback

import android.content.Context
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream

/**
 * 反馈页资源读取策略：热更新覆盖包优先，安装包 assets 兜底。
 *
 * 只放行白名单里的三个文件，其它路径一律交给 WebView 当 404 处理，
 * 避免本地目录被当作文件系统任意读取。
 */
class FeedbackAssetHandler(private val context: Context) : WebViewAssetLoader.PathHandler {

    private val types = mapOf(
        FeedbackBundle.ENTRY to "text/html",
        "styles.css" to "text/css",
        "app.js" to "application/javascript"
    )

    override fun handle(path: String): WebResourceResponse? {
        val name = path.trimStart('/')
        val mime = types[name] ?: return null
        val bytes = FeedbackBundle.read(context, name) ?: return null
        return WebResourceResponse(mime, "utf-8", ByteArrayInputStream(bytes))
    }
}
