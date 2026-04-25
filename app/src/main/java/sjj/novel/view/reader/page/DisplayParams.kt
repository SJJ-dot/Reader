package sjj.novel.view.reader.page

import com.sjianjun.reader.utils.dpToPx

class DisplayParams {
    var width = 0
    var height = 0

    var tipHeight = dpToPx(28f)

    var insetLeft = dpToPx(8f)
    var insetTop = dpToPx(2f) //包括状态栏高度
    var insetRight = dpToPx(8f)
    var insetBottom = dpToPx(2f) //包括导航按钮高度

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
    var letterSpacing = 0f

    val contentLeft: Float get() = insetLeft
    val contentTop: Float get() = tipHeight + insetTop
    val contentRight: Float get() = width - insetRight
    val contentBottom: Float get() = height - insetBottom
    val contentWidth: Float get() = contentRight - contentLeft
    val contentHeight: Float get() = contentBottom - contentTop

    fun setInsets(left: Float, top: Float, right: Float, bottom: Float) {
        insetLeft = left
        insetTop = top
        insetRight = right
        insetBottom = bottom
    }

}