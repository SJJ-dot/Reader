package sjj.novel.view.reader.page

class TxtLine(
    var txt: String,
    var isTitle: Boolean,
    var height: Float,
    var width: Float,
    var isParaEnd: Boolean
) {

    var top = 0f


    var bottom = 0f


    var left = 0f


    var right = 0f

    /**
     * 当前行号
     */

    var index = 0


    var clusterStart = 0


    var clusterLeft = FloatArray(0)


    var clusterRight = FloatArray(0)

    /** grapheme cluster boundaries: [0, end1, end2, ..., txt.length] */

    var clusterBoundaries: IntArray = IntArray(0)

    fun setLeftAndRight(left: FloatArray, right: FloatArray, size: Int) {
        this.clusterLeft = left.copyOf(size)
        this.clusterRight = right.copyOf(size)
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
                ", clusterStart=" + clusterStart +
                '}'
    }


}