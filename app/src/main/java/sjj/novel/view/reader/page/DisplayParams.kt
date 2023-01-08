package sjj.novel.view.reader.page

import android.content.Context
import sjj.novel.view.reader.utils.ScreenUtils

class DisplayParams(context: Context) {
    private val screenUtils = ScreenUtils(context)
    var width = 0
    var height = 0

    var tipHeight = screenUtils.dpToPx(28)

    var paddingWidth = screenUtils.dpToPx(8)
    var paddingHeight = screenUtils.dpToPx(5)

    /**
     * 段落间距
     */
    var titlePara = 0
    var textPara = 0

    /**
     * 行间距
     */
    var titleInterval = 0
    var textInterval = 0

    val contentLeft get() = paddingWidth
    val contentTop get() = tipHeight + paddingHeight
    val contentRight get() = width - paddingWidth
    val contentBottom get() = height - paddingHeight
    val contentWidth get() = contentRight - contentLeft
    val contentHeight get() = contentBottom - contentTop

}