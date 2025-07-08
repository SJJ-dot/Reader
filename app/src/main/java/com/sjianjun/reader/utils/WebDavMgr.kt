package com.sjianjun.reader.utils

import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.DbFactory
import io.legado.app.lib.webdav.Authorization
import io.legado.app.lib.webdav.WebDav
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.launch
import sjj.alog.Log

object WebDavMgr {
    const val WEB_DAV_ID = "webDavId.txt"
    const val BOOK_INFO_LIST = "bookInfoList.json"
    const val READING_RECORD_LIST = "readingRecordList.json"
    private val dao get() = DbFactory.db.dao()
    private var job: Job? = null
    private var lastToastTime = 0L

    private fun webDav(relativePath: String) = globalConfig.let {
        val auth = Authorization(it.webdavUsername ?: "", it.webdavPassword ?: "")
        WebDav("${it.webdavUrl}${it.webdavSubdir}/${relativePath}", auth)
    }

    suspend fun setAccount(
        url: String,
        username: String,
        password: String,
        subDir: String
    ): Result<Unit> {
        var server = url
        if (!server.endsWith("/")) {
            server = "${server}/"
        }
        val auth = Authorization(username, password)
        val makeDir = WebDav("${server}${subDir}/", auth).makeAsDir()
        return makeDir
    }

    fun init() {
        job?.cancel()
        job = CoroutineScope(Dispatchers.IO).launch {
            launch {
                Log.i("监听书籍信息变化")
                var lastBook = emptySet<String>()
                dao.getAllBook().debounce(1000).collect {
                    val bookListInfo = it.map { it.title + it.author + it.bookSourceId }.toSet()
                    Log.i("同步书籍记录 change:${lastBook != bookListInfo}")
                    if (lastBook != bookListInfo) {
                        sync { uploadBookInfo(it) }
                        lastBook = bookListInfo
                    }
                }
            }
            launch {
                Log.i("监听阅读记录变化")
                var lastRecord = emptySet<String>()
                dao.getAllReadingRecord().debounce(1000).collect {
                    val bookListInfo = it.map { it.bookId + it.chapterIndex + it.offest }.toSet()
                    Log.i("同步阅读记录 change:${lastRecord != bookListInfo}")
                    if (lastRecord != bookListInfo) {
                        sync { uploadReadingRecord(it) }
                        lastRecord = bookListInfo
                    }
                }
            }
        }

    }


    private suspend fun sync(run: suspend WebDavMgr.() -> Unit = {}) = withIo {
        if (needPull()) {
            pull()
            val webDavId =
                webDav(WEB_DAV_ID).upload(globalConfig.webDavId!!.toByteArray(Charsets.UTF_8))
            Log.i("webDavId 同步结果：${webDavId} put ID:${globalConfig.webDavId}")
        }

        WebDavMgr.run()
    }

    private suspend fun uploadBookInfo(bookList: List<Book>): Result<Unit> = withIo {
        if (!globalConfig.webdavHasCfg) {
            return@withIo Result.failure(Exception("没有配置webDav账号信息"))
        }
        val upload =
            webDav(BOOK_INFO_LIST).upload(gson.toJson(bookList).toByteArray())
        if (upload.isFailure) {
            if (System.currentTimeMillis() - lastToastTime > 1 * 60 * 60 * 1000) {
                lastToastTime = System.currentTimeMillis()
                toast("书籍信息上传失败")
            }
        }
        Log.i("上传阅读书籍信息:${upload}")
        return@withIo upload
    }

    private suspend fun uploadReadingRecord(record: List<ReadingRecord>) = withIo {
        if (!globalConfig.webdavHasCfg) {
            return@withIo Result.failure(Exception("没有配置webDav账号信息"))
        }
        val upload = webDav(READING_RECORD_LIST).upload(gson.toJson(record).toByteArray())
        if (upload.isFailure) {
            if (System.currentTimeMillis() - lastToastTime > 1 * 60 * 60 * 1000) {
                lastToastTime = System.currentTimeMillis()
                toast("阅读记录上传失败")
            }
        }
        Log.i("上传阅读记录:${upload}")
        return@withIo upload
    }

    private suspend fun pull(): Result<Unit> = runCatching {
        if (!globalConfig.webdavHasCfg) {
            return Result.failure(Exception("没有配置webDav账号信息"))
        }
        Log.e("同步书籍信息")
        val readingList =
            gson.fromJson<List<ReadingRecord>>(webDav(READING_RECORD_LIST).downloadStr())
                ?: emptyList()

        val bookList =
            gson.fromJson<List<Book>>(webDav(BOOK_INFO_LIST).downloadStr()) ?: emptyList()

        val list = dao.insertBook(bookList)
        Log.i("保存书籍数据：${list} $bookList")
        val list1 = dao.insertReadingRecordList(readingList)
        Log.i("保存阅读记录：${list1} $readingList")
    }

    private suspend fun needPull(): Boolean {
        if (!globalConfig.webdavHasCfg) {
            return false
        }
        try {
            val exists = webDav(WEB_DAV_ID).exists()
            if (!exists) {
                return false
            }
            val webDavId = webDav(WEB_DAV_ID).downloadStr()
            if (webDavId != globalConfig.webDavId) {
                Log.i("ID不一致 loc:${globalConfig.webDavId} remote:${webDavId}")
                return true
            }
            return false
        } catch (e: Exception) {
            return false
        }
    }

}