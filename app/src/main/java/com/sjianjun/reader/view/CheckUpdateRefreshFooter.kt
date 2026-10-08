package com.sjianjun.reader.view

import android.content.Context
import android.util.AttributeSet
import com.scwang.smart.refresh.layout.api.RefreshFooter

/**
 * 上拉「检查更新」尾部：只拉取最新章节与本地已有章节拼接（增量）
 */
class CheckUpdateRefreshFooter @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RefreshTextComponent(context, attrs), RefreshFooter {

    override fun pullingText(): String = "上拉检查更新"
}
