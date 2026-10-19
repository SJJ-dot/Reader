package com.sjianjun.reader.tts.v2.session

/**
 * 一章的朗读内容（已做文字处理并按段拆分好）
 */
data class LoadedChapter(
    val index: Int,
    val title: String,
    /** 段落列表：与阅读页排版段落口径一致，会话层按段推进 */
    val paragraphs: List<String>,
)

/**
 * 小说内容来源：会话层只依赖这个接口，具体实现由「网络小说加载服务」提供
 * （加载、分页拼接、文字处理、缓存、去重都在实现里）。
 *
 * 约束：
 * - [loadChapter] 内部按章去重，同一章并发请求只实际加载一次；
 * - [prefetch] 只是"提前准备"，结果进缓存，之后 [loadChapter] 直接命中；
 * - 两者都响应协程取消（会话切章/结束时取消旧请求）。
 */
interface NovelContentSource {

    /** 加载某一章；失败返回 null（会话层据此结束会话，不跳章、不漏内容） */
    suspend fun loadChapter(bookId: String, chapterIndex: Int): LoadedChapter?

    /** 预取某一章（不阻塞，失败不影响朗读） */
    fun prefetch(bookId: String, chapterIndex: Int)

    /** 章节总数：用于判断"全书读完"（返回 0 表示未知，按"还能继续读"处理） */
    suspend fun chapterCount(bookId: String): Int
}
