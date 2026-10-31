package com.sjianjun.reader.module.bookcity

import android.content.Context
import android.os.Bundle
import android.os.Parcel
import java.io.File
import com.sjianjun.reader.BuildConfig

/**
 * 把每个标签页的 WebView 原生状态快照（含前进/后退历史）经 Parcel 落盘到文件，
 * 使“退出再进 / 进程被杀”后仍能恢复前进后退栈（WebView.saveState 本身不跨进程）。
 *
 * 已用真机验证：restoreState 在全新 WebView 上能恢复历史，且 Bundle 经 Parcel marshall 往返后依旧有效。
 *
 * 注意：每个标签页各自按需调用本对象（id 即标签 id，String），不再持有标签列表。
 */
object TabStateStore {
    private fun dir(context: Context) =
        File(context.filesDir, "browser_state").also { if (!it.exists()) it.mkdirs() }

    private fun file(context: Context, id: String) = File(dir(context), "$id.bin")

    fun write(context: Context, id: String, state: Bundle) {
        val bytes = runCatching { marshall(state) }.getOrNull() ?: return
        runCatching { file(context, id).writeBytes(bytes) }
        if (BuildConfig.DEBUG) android.util.Log.i("HISTBUG", "TabStateStore.write id=$id bytes=${bytes.size} path=${file(context, id).absolutePath} exists=${file(context, id).exists()}")
    }

    fun read(context: Context, id: String): Bundle? {
        val f = file(context, id)
        if (!f.exists()) {
            if (BuildConfig.DEBUG) android.util.Log.i("HISTBUG", "TabStateStore.read id=$id -> 文件不存在")
            return null
        }
        val bundle = runCatching {
            val bytes = f.readBytes()
            if (bytes.isEmpty()) null else unmarshall(bytes)
        }.getOrNull()
        if (BuildConfig.DEBUG) android.util.Log.i("HISTBUG", "TabStateStore.read id=$id fileLen=${f.length()} -> ${if (bundle != null) "ok" else "null"}")
        return bundle
    }

    fun delete(context: Context, id: String) {
        runCatching { file(context, id).delete() }
    }

    private fun marshall(b: Bundle): ByteArray {
        val p = Parcel.obtain()
        try {
            b.writeToParcel(p, 0)
            return p.marshall()
        } finally {
            p.recycle()
        }
    }

    private fun unmarshall(bytes: ByteArray): Bundle {
        val p = Parcel.obtain()
        try {
            p.unmarshall(bytes, 0, bytes.size)
            p.setDataPosition(0)
            return Bundle.CREATOR.createFromParcel(p)
        } finally {
            p.recycle()
        }
    }
}
