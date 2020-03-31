package com.sjianjun.reader.utils

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager


fun View?.hideKeyboard() {
    if (this == null) {
        return
    }
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
}

fun View?.showKeyboard() {
    if (this == null) {
        return
    }
    isFocusable = true
    isFocusableInTouchMode = true
    requestFocus()
    val inputManager: InputMethodManager =
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    inputManager.showSoftInput(this, 0)
}