package com.sjianjun.reader.utils

import android.text.Html
import android.text.Spanned

fun String?.html(): Spanned {
    return Html.fromHtml(this ?: "", Html.FROM_HTML_MODE_COMPACT)
}