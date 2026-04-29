package com.sjianjun.reader.utils

import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment

fun DialogFragment.applyEdgeToEdgeDialogBar(colorRes: Int, lightBars: Boolean) {
    val window = dialog?.window ?: return
    val color = ContextCompat.getColor(requireContext(), colorRes)
    window.statusBarColor = color
    window.navigationBarColor = color
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
        isAppearanceLightStatusBars = lightBars
        isAppearanceLightNavigationBars = lightBars
    }
}