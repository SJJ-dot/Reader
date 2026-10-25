package com.sjianjun.reader.tts.v2

/**
 * Edge 音色列表（单一来源：朗读设置弹窗与字幕浮窗共用）。
 */
object TtsVoices {

    /** 显示名 to 参数 */
    val options: List<Pair<String, String>> = listOf(
        "晓晓" to "zh-CN-XiaoxiaoNeural",
        "小艺" to "zh-CN-XiaoyiNeural",
        "云健" to "zh-CN-YunjianNeural",
        "云希" to "zh-CN-YunxiNeural",
        "云夏" to "zh-CN-YunxiaNeural",
        "云扬" to "zh-CN-YunyangNeural",
        "小北(辽宁方言)" to "zh-CN-liaoning-XiaobeiNeural",
        "小妮(陕西方言)" to "zh-CN-shaanxi-XiaoniNeural",
    )

    val defaultParam: String = options.first().second

    fun params(): List<String> = options.map { it.second }

    fun displayNames(): List<String> = options.map { it.first }

    fun displayOf(param: String?): String =
        options.firstOrNull { it.second == param }?.first ?: (param ?: defaultParam)

    /** 在列表中前后切换（循环） */
    fun shift(param: String?, step: Int): String {
        if (options.isEmpty()) return param ?: defaultParam
        val index = options.indexOfFirst { it.second == param }.takeIf { it >= 0 } ?: 0
        val size = options.size
        val next = ((index + step) % size + size) % size
        return options[next].second
    }
}
