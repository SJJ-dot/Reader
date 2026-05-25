@file:Suppress("unused", "UnusedReceiverParameter")

package io.legado.app.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.PendingIntent.getActivity
import android.app.PendingIntent.getBroadcast
import android.app.PendingIntent.getService
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.preference.PreferenceManager
import android.provider.Settings
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.help.IntentHelp
import splitties.init.appCtx
import java.io.File
import kotlin.system.exitProcess

inline fun <reified A : Activity> Context.startActivity(configIntent: Intent.() -> Unit = {}) {
    val intent = Intent(this, A::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.apply(configIntent)
    startActivity(intent)
}


inline fun <reified T : Service> Context.startService(configIntent: Intent.() -> Unit = {}) {
    startService(Intent(this, T::class.java).apply(configIntent))
}

inline fun <reified T : Service> Context.stopService() {
    stopService(Intent(this, T::class.java))
}

@SuppressLint("UnspecifiedImmutableFlag")
inline fun <reified T : Service> Context.servicePendingIntent(
    action: String,
    requestCode: Int = 0,
    configIntent: Intent.() -> Unit = {},
): PendingIntent? {
    val intent = Intent(this, T::class.java)
    intent.action = action
    configIntent.invoke(intent)
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        FLAG_UPDATE_CURRENT or FLAG_MUTABLE
    } else {
        FLAG_UPDATE_CURRENT
    }
    return getService(this, requestCode, intent, flags)
}

@SuppressLint("UnspecifiedImmutableFlag")
fun Context.activityPendingIntent(
    intent: Intent,
    action: String,
): PendingIntent? {
    intent.action = action
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        FLAG_UPDATE_CURRENT or FLAG_MUTABLE
    } else {
        FLAG_UPDATE_CURRENT
    }
    return getActivity(this, 0, intent, flags)
}

@SuppressLint("UnspecifiedImmutableFlag")
inline fun <reified T : Activity> Context.activityPendingIntent(
    action: String,
    configIntent: Intent.() -> Unit = {},
): PendingIntent? {
    val intent = Intent(this, T::class.java)
    intent.action = action
    configIntent.invoke(intent)
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        FLAG_UPDATE_CURRENT or FLAG_MUTABLE
    } else {
        FLAG_UPDATE_CURRENT
    }
    return getActivity(this, 0, intent, flags)
}

@SuppressLint("UnspecifiedImmutableFlag")
inline fun <reified T : BroadcastReceiver> Context.broadcastPendingIntent(
    action: String,
    configIntent: Intent.() -> Unit = {},
): PendingIntent? {
    val intent = Intent(this, T::class.java)
    intent.action = action
    configIntent.invoke(intent)
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        FLAG_UPDATE_CURRENT or FLAG_MUTABLE
    } else {
        FLAG_UPDATE_CURRENT
    }
    return getBroadcast(this, 0, intent, flags)
}

fun Context.startForegroundServiceCompat(intent: Intent) {
    try {
        startService(intent)
    } catch (e: IllegalStateException) {
        ContextCompat.startForegroundService(this, intent)
    }
}

val Context.defaultSharedPreferences: SharedPreferences
    get() = PreferenceManager.getDefaultSharedPreferences(this)

fun Context.getPrefBoolean(key: String, defValue: Boolean = false) =
    defaultSharedPreferences.getBoolean(key, defValue)

fun Context.putPrefBoolean(key: String, value: Boolean = false) =
    defaultSharedPreferences.edit { putBoolean(key, value) }

fun Context.getPrefInt(key: String, defValue: Int = 0) =
    defaultSharedPreferences.getInt(key, defValue)

fun Context.putPrefInt(key: String, value: Int) =
    defaultSharedPreferences.edit { putInt(key, value) }

fun Context.getPrefLong(key: String, defValue: Long = 0L) =
    defaultSharedPreferences.getLong(key, defValue)

fun Context.putPrefLong(key: String, value: Long) =
    defaultSharedPreferences.edit { putLong(key, value) }

fun Context.getPrefString(key: String, defValue: String? = null) =
    defaultSharedPreferences.getString(key, defValue)

fun Context.putPrefString(key: String, value: String?) =
    defaultSharedPreferences.edit { putString(key, value) }

fun Context.getPrefStringSet(
    key: String,
    defValue: MutableSet<String>? = null,
): MutableSet<String>? = defaultSharedPreferences.getStringSet(key, defValue)

fun Context.putPrefStringSet(key: String, value: MutableSet<String>) =
    defaultSharedPreferences.edit { putStringSet(key, value) }

fun Context.removePref(key: String) =
    defaultSharedPreferences.edit { remove(key) }


fun Context.getCompatColor(@ColorRes id: Int): Int = ContextCompat.getColor(this, id)

fun Context.getCompatDrawable(@DrawableRes id: Int): Drawable? = ContextCompat.getDrawable(this, id)

fun Context.getCompatColorStateList(@ColorRes id: Int): ColorStateList? =
    ContextCompat.getColorStateList(this, id)

fun Context.checkSelfUriPermission(uri: Uri, modeFlags: Int): Int =
    checkUriPermission(uri, Process.myPid(), Process.myUid(), modeFlags)

fun Context.restart() {
    val intent: Intent? = packageManager.getLaunchIntentForPackage(packageName)
    intent?.let {
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        startActivity(intent)
        //杀掉以前进程
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }
}

fun Context.sendToClip(text: String) {
    val clipboard = appCtx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clipData = ClipData.newPlainText("text", text)
    clipboard?.setPrimaryClip(clipData)
    longToastOnUi(R.string.copy_complete)
}


val Context.externalFiles: File
    get() = this.getExternalFilesDir(null) ?: this.filesDir

val Context.externalCache: File
    get() = this.externalCacheDir ?: this.cacheDir

fun Context.openUrl(url: String) {
    try {
        startActivity(IntentHelp.getBrowserIntent(url))
    } catch (e: Exception) {
        toastOnUi(e.localizedMessage ?: "open url error")
        e.printOnDebug()
    }
}

fun Context.openUrl(uri: Uri) {
    try {
        startActivity(IntentHelp.getBrowserIntent(uri))
    } catch (e: Exception) {
        toastOnUi(e.localizedMessage ?: "open url error")
        e.printOnDebug()
    }
}