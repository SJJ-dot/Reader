package com.sjianjun.reader.event

object EventKey {
    /** 阅读页刷新章节内容 */
    val CHAPTER_SYNC_FORCE = "CHAPTER_SYNC_FORCE"

    /** 阅读页标记章节内容错误 */
    val CHAPTER_CONTENT_ERROR = "CHAPTER_CONTENT_ERROR"

    /** 阅读页打开章节列表 */
    val CHAPTER_LIST = "CHAPTER_LIST"

    /** 阅读页开始缓存章节 */
    val CHAPTER_LIST_CAHE = "CHAPTER_LIST_CACHE"

    /** 阅读页tts开始语音阅读 */
    val CHAPTER_SPEAK = "CHAPTER_SPEAK"

    /** 阅读页 页面样式修改 */
    val CUSTOM_PAGE_STYLE = "CUSTOM_PAGE_STYLE"

    /** 使用webview打开链接 */
    val BROWSER_OPEN = "BROWSER_OPEN"

    /** 打开网页菜单 */
    val WEB_VIEW_SETTINGS = "WEB_VIEW_SETTINGS"

    /** 阅读页净化替换规则变更 */
    val REPLACEMENT_RULES_CHANGED = "REPLACEMENT_RULES_CHANGED"

    /** 书籍选中的书源变更 */
    val BOOK_SOURCE_CHANGED = "BOOK_SOURCE_CHANGED"

    /** 书籍封面变更 */
    val BOOK_COVER_CHANGED = "BOOK_COVER_CHANGED"
}