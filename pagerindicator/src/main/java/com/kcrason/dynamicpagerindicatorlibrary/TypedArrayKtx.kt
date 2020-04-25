package com.kcrason.dynamicpagerindicatorlibrary

import android.content.res.Resources
import android.content.res.TypedArray
import android.util.TypedValue
import androidx.annotation.StyleableRes

fun TypedArray.getCompatColor(theme:Resources.Theme,@StyleableRes index: Int, typedValue: TypedValue): Int? {
    if (getValue(index, typedValue)) {
        if (typedValue.type == TypedValue.TYPE_ATTRIBUTE) {
            theme.resolveAttribute(typedValue.data, typedValue, true)
            return typedValue.data
        } else {
            return typedValue.data
        }
    } else if (theme.resolveAttribute(typedValue.data, typedValue, true)) {
        return typedValue.data
    }
    return null
}