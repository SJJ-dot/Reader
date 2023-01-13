package sjj.novel.view.reader.page

import sjj.novel.view.reader.page.TxtLine
import java.util.*

class TxtLine(
    @JvmField var txt: String,
    @JvmField var isTitle: Boolean,
    @JvmField var height: Int,
    @JvmField var width: Int
) {
    @JvmField
    var top = 0

    @JvmField
    var bottom = 0

    @JvmField
    var left = 0

    @JvmField
    var right = 0

    /**
     * 当前行号
     */
    @JvmField
    var index = 0

    @JvmField
    var charStart = 0

    @JvmField
    val charLeft = IntArray(txt.length)

    @JvmField
    val charRight = IntArray(txt.length)
    fun addLeftOfRight(index: Int, leftOf: Int, rightOf: Int) {
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