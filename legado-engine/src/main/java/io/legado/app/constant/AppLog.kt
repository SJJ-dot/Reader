package io.legado.app.constant

import android.util.Log
import io.legado.app.BuildConfig
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.LogUtils
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx

object AppLog {

    @Synchronized
    fun put(message: String?, throwable: Throwable? = null, toast: Boolean = false) {
        message ?: return
        if (toast) {
            appCtx.toastOnUi(message)
        }
        if (throwable == null) {
            LogUtils.d("AppLog", message)
        } else {
            LogUtils.d("AppLog", "$message\n${throwable.stackTraceToString()}")
        }
        if (BuildConfig.DEBUG) {
            val stackTrace = Thread.currentThread().stackTrace
            Log.e(stackTrace[3].className, message, throwable)
        }
    }

    fun putDebug(message: String?, throwable: Throwable? = null) {
        if (AppConfig.recordLog) {
            put(message, throwable)
        }
    }

}