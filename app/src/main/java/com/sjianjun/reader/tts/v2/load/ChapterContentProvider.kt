package com.sjianjun.reader.tts.v2.load

/**
 * 章节正文来源：只负责"取到一页拼好的正文 + 标题"。
 *
 * 具体实现（[BookChapterContentProvider]）做：数据库缓存优先 → 书源脚本抓取 → 分页拼接 →
 * 与阅读页一致的文字处理（简繁/全角/净化规则）。
 *
 * 分段、缓存、去重、重试由 [NovelLoadService] 负责，两者分开便于单独测试。
 */
interface ChapterContentProvider {

    /**
     * 加载一章正文。
     *
     * @return 失败返回 null；被取消时应该抛 CancellationException（不要吞掉）
     */
    suspend fun load(bookId: String, chapterIndex: Int): ProvidedChapter?

    /** 章节总数（用于判断"全书读完"，避免把最后一章之后当成加载失败） */
    suspend fun chapterCount(bookId: String): Int
}

/**
 * 一章正文（已做文字处理，未分段）
 */
data class ProvidedChapter(
    val title: String,
    /** 纯文本正文，段落之间用换行分隔 */
    val text: String,
)
