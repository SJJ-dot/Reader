package io.legado.app.utils

import android.app.Dialog
import androidx.fragment.app.DialogFragment



fun DialogFragment.setLayout(widthMix: Float, height: Int) {
    dialog?.setLayout(widthMix, height)
}

fun Dialog.setLayout(widthMix: Float, height: Int) {
    val metrics = context.resources.displayMetrics
    window?.setLayout(
        (metrics.widthPixels * widthMix).toInt(),
        height
    )
}