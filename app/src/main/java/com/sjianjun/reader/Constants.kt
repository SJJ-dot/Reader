package com.sjianjun.reader

const val BOOK_ID = "BOOK_ID"
const val BOOK_SOURCE_ID = "BOOK_SOURCE_ID"

/** 打开阅读器后直接唤起设置菜单（菜单里有朗读播放卡片） */
const val SHOW_TTS_CARD = "show_tts_card"

const val BOOK_TITLE = "BOOK_TITLE"
const val BOOK_AUTO_REFRESH = "BOOK_AUTO_REFRESH"
const val BOOK_READ_ONLY = "BOOK_READ_ONLY"
/** 打开换源弹窗时默认进入「内容对比」模式（阅读页打开时传 true） */
/** 打开章节列表时指定定位到的章节序号（换源内容对比里的「目录」） */
const val CHAPTER_INDEX = "CHAPTER_INDEX"

/** 打开换源内容对比时，当前阅读页第一行的文本（用于在新书源正文里按内容定位） */
const val PAGE_FIRST_LINE = "PAGE_FIRST_LINE"
const val URL = "url"
const val WEB_ID = "web_id"
const val CROSS_ORIGIN_MODE = "cross_origin_mode"