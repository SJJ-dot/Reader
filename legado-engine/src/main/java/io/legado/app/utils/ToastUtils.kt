@file:Suppress("unused")

package io.legado.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.fragment.app.Fragment
import splitties.init.appCtx

private var toast: Toast? = null

private var toastLegacy: Toast? = null

fun Context.toastOnUi(message: Int, duration: Int = Toast.LENGTH_SHORT) {
    toastOnUi(getString(message), duration)
}

@SuppressLint("InflateParams")
@Suppress("DEPRECATION")
fun Context.toastOnUi(message: CharSequence?, duration: Int = Toast.LENGTH_SHORT) {
    if (message.isNullOrBlank()) return
    runOnUI {
        Toast.makeText(appCtx, message, duration).show()
    }
}

fun Context.toastOnUiLegacy(message: CharSequence) {
    if (message.isNullOrBlank()) return
    runOnUI {
        Toast.makeText(appCtx, message, Toast.LENGTH_SHORT).show()
    }
}

fun Context.longToastOnUi(message: Int) {
    toastOnUi(message, Toast.LENGTH_LONG)
}

fun Context.longToastOnUi(message: CharSequence?) {
    toastOnUi(message, Toast.LENGTH_LONG)
}

fun Context.longToastOnUiLegacy(message: CharSequence) {
    if (message.isNullOrBlank()) return
    runOnUI {
        Toast.makeText(appCtx, message, Toast.LENGTH_SHORT).show()
    }
}

fun Fragment.toastOnUi(message: Int) = requireActivity().toastOnUi(message)

fun Fragment.toastOnUi(message: CharSequence) = requireActivity().toastOnUi(message)

fun Fragment.longToast(message: Int) = requireContext().longToastOnUi(message)

fun Fragment.longToast(message: CharSequence) = requireContext().longToastOnUi(message)
