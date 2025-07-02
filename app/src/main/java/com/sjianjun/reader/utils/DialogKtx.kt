package com.sjianjun.reader.utils

import android.app.Activity
import android.app.Dialog
import android.view.View
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.Nullable
import com.sjianjun.reader.R
import com.sjianjun.reader.view.click

fun dialog(activity: Activity, show: Boolean = true, apply: Dialog.() -> Unit = {}): Dialog {
    val dialog = Dialog(activity, R.style.dialog_style).apply(apply)
    if (show) {
        dialog.show()
    }
    return dialog
}

fun <T : View> Dialog.view(@IdRes id: Int): T? {
    val view = window?.decorView
    return view?.findViewById(id)
}

fun Dialog.setText(@IdRes id: Int, text: CharSequence?): Dialog {
    view<TextView>(id)?.text = text ?: ""
    return this
}


fun Dialog.onClick(
    l: (view: View, dialog: Dialog) -> Unit,
    @IdRes vararg ids: Int
): Dialog {
    return onClick(l, true, *ids)
}

fun Dialog.onClick(
    l: (view: View, dialog: Dialog) -> Unit,
    dismiss: Boolean,
    @IdRes vararg ids: Int
): Dialog {
    ids.forEach {
        view<View>(it)?.click { view ->
            if (dismiss) {
                dismiss()
            }
            l(view, this)
        }
    }
    return this
}

fun Dialog.dismiss(@IdRes vararg ids: Int): Dialog {
    return dismiss(true, *ids)
}

fun Dialog.dismiss(dismiss: Boolean, @IdRes vararg ids: Int): Dialog {
    return onClick({ _, dialog ->
        dialog.dismiss()
    }, dismiss, *ids)
}