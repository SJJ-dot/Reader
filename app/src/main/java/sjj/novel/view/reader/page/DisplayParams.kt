package sjj.novel.view.reader.page

import com.sjianjun.reader.utils.dpToPx

class DisplayParams {
    var width = 0
    var height = 0

    var tipHeight = dpToPx(28f)

    var paddingWidth = dpToPx(8f)
    var paddingHeight = dpToPx(5f)

    /**
     * 段落间距
     */
    var titlePara = 0f
    var textPara = 0f

    /**
     * 行间距
     */
    var titleInterval = 0f
    var textInterval = 0f

    val contentLeft: Float get() = paddingWidth
    val contentTop: Float get() = tipHeight + paddingHeight
    val contentRight: Float get() = width - paddingWidth
    val contentBottom: Float get() = height - paddingHeight
    val contentWidth: Float get() = contentRight - contentLeft
    val contentHeight: Float get() = contentBottom - contentTop

}