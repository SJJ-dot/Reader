package com.sjianjun.reader.module.reader

object ReaderClickAreaAction {
    const val NONE = 0
    const val PREV_PAGE = 1
    const val NEXT_PAGE = 2
    const val MENU = 3
    const val PREV_CHAPTER = 4
    const val NEXT_CHAPTER = 5
    const val DIRECTORY = 6

    val allActionIds = listOf(NONE, PREV_PAGE, NEXT_PAGE, MENU, PREV_CHAPTER, NEXT_CHAPTER, DIRECTORY)

    private val defaultActions = listOf(
        PREV_PAGE, MENU, NEXT_PAGE,
        PREV_PAGE, MENU, NEXT_PAGE,
        PREV_PAGE, MENU, NEXT_PAGE
    )

    private val actionLabels = mapOf(
        NONE to "无操作",
        PREV_PAGE to "上一页",
        NEXT_PAGE to "下一页",
        MENU to "菜单",
        PREV_CHAPTER to "上一章",
        NEXT_CHAPTER to "下一章",
        DIRECTORY to "目录"
    )

    private val cellLabels = listOf("左上", "上中", "右上", "左中", "中间", "右中", "左下", "下中", "右下")

    fun defaultConfig(): List<Int> = defaultActions.toList()

    fun normalize(actions: List<Int>?): List<Int> {
        if (actions == null || actions.size != 9) {
            return defaultConfig()
        }
        return actions.mapIndexed { index, action ->
            if (action in allActionIds) action else defaultActions[index]
        }
    }

    fun label(action: Int): String = actionLabels[action] ?: actionLabels.getValue(NONE)

    fun cellLabel(index: Int): String = cellLabels.getOrElse(index) { "区域${index + 1}" }

    fun dialogOptions(): Array<String> = allActionIds.map(::label).toTypedArray()

    fun optionIndexForAction(action: Int): Int = allActionIds.indexOf(action).coerceAtLeast(0)

    fun actionForOption(which: Int): Int = allActionIds.getOrElse(which) { NONE }

    fun cellIndexOf(x: Float, y: Float, width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) {
            return 4
        }
        val column = ((x / (width / 3f)).toInt()).coerceIn(0, 2)
        val row = ((y / (height / 3f)).toInt()).coerceIn(0, 2)
        return row * 3 + column
    }
}

