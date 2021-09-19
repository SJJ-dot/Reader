package com.sjianjun.reader.utils

import android.os.Environment
import com.sjianjun.reader.BuildConfig

const val BOOK_URL = "BOOK_URL"
const val CHAPTER_URL = "CHAPTER_URL"
const val JAVA_SCRIPT_SOURCE = "JAVA_SCRIPT_SOURCE"

const val BOOK_TITLE = "BOOK_TITLE"
const val BOOK_AUTHOR = "BOOK_AUTHOR"


const val PAGE_ID = "page_page_script_id"


const val SEARCH_KEY = "SEARCH_KEY"


/**
 * github release info 返回的apk类型信息
 */
const val CONTENT_TYPE_ANDROID = "application/vnd.android.package-archive"

/**
 * 起点中文网 不能修改source
 */
const val BOOK_SOURCE_QI_DIAN = "起点中文网"
const val BOOK_SOURCE = "JS_SOURCE"
const val JS_FIELD_REQUEST_DELAY = "REQUEST_DELAY"

//txt 文件导入
const val BOOK_SOURCE_FILE_IMPORT = "文件导入"

const val GITHUB_TOKEN = "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"
const val URL_ASSETS_BASE =
    "https://raw.githubusercontent.com/SJJ-dot/Reader/master/app/src/main/assets/"
const val URL_RELEASE_INFO = "https://api.github.com/repos/SJJ-dot/Reader/releases/latest"
const val URL_RELEASE_DEF =
    "https://github.com/SJJ-dot/Reader/releases/download/0.5.61/reader-master-release.364.-0.5.61.apk"
const val URL_REPO = "https://github.com/SJJ-dot/Reader"

/**
 * 未找到首发站点
 */
const val STARTING_STATION_BOOK_SOURCE_EMPTY = "STARTING_STATION_BOOK_SOURCE_NOT_FOUND"

const val WEB_VIEW_UA_DESKTOP =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.0 Safari/537.36 EdgA/44.11.2.4122"
const val WEB_VIEW_UA_ANDROID =
    "Mozilla/5.0 (Linux; Android 9; MIX 2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.0 Mobile Safari/537.36 EdgA/44.11.2.4122"

val APP_DATA_DIR by lazy { Environment.getExternalStorageDirectory().absolutePath + "/" + BuildConfig.APPLICATION_ID + "/database" }
val APP_DATABASE_FILE by lazy { "$APP_DATA_DIR/app_database" }


