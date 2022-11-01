package com.sjianjun.reader.repository

import com.sjianjun.reader.preferences.globalConfig
import io.legado.app.lib.webdav.Authorization
import io.legado.app.lib.webdav.WebDav

object WebDavMgr {

    suspend fun init(url: String, username: String, password: String, subDir: String): Result<Unit> {
        var server = url
        if (!server.endsWith("/")) {
            server = "${server}/"
        }
        val auth = Authorization(username, password)
        val makeDir = WebDav("${server}${subDir}/", auth).makeAsDir()
        if (makeDir.isSuccess) {
            globalConfig.apply {
                webdavUrl = server
                webdavUsername = username
                webdavPassword = password
                webdavSubdir = subDir
                globalConfig.webdavHasCfg = true
            }
        }
        return makeDir
    }

}