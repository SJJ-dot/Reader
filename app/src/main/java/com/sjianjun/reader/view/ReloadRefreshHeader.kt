package com.sjianjun.reader.view

import android.content.Context
import android.util.AttributeSet
import com.scwang.smart.refresh.layout.api.RefreshHeader

/**
 * 下拉「重新加载」头部：以远端返回的完整章节列表重建本地章节（全量更新）
 */
class ReloadRefreshHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RefreshTextComponent(context, attrs), RefreshHeader {

    override fun pullingText(): String = "下拉重新加载"

    override fun releaseText(): String = "松手重新加载"

    override fun loadingText(): String = "正在重新加载…"

    override fun finishText(success: Boolean): String =
        if (success) "重新加载完成" else "重新加载失败"
}
