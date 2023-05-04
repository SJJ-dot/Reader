package sjj.novel.view.reader.page

import sjj.novel.view.reader.page.TxtLine
import java.util.*

class TxtLine(
    @JvmField var txt: String,
    @JvmField var isTitle: Boolean,
    @JvmField var height: Float,
    @JvmField var width: Float,
    @JvmField var isParaEnd: Boolean
) {
    @JvmField
    var top = 0f

    @JvmField
    var bottom = 0f

    @JvmField
    var left = 0f

    @JvmField
    var right = 0f

    /**
     * 当前行号
     */
    @JvmField
    var index = 0

    @JvmField
    var charStart = 0

    @JvmField
    val charLeft = FloatArray(txt.length)

    @JvmField
    val charRight = FloatArray(txt.length)
    fun setLeftOfRight(index: Int, leftOf: Float, rightOf: Float) {
        charLeft[index] = leftOf
        charRight[index] = rightOf
    }

    override fun toString(): String {
        return "TxtLine{" +
                "txt='" + txt + '\'' +
                ", isTitle=" + isTitle +
                ", top=" + top +
                ", bottom=" + bottom +
                ", left=" + left +
                ", right=" + right +
                ", index=" + index +
                ", charStart=" + charStart +
                '}'
    }


}