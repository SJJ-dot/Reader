package com.sjianjun.reader.utils

import androidx.recyclerview.widget.RecyclerView

fun RecyclerView.ViewHolder.setRecyclable(recyclable: Boolean) {
    if (isRecyclable && !recyclable) {
        setIsRecyclable(recyclable)
    } else if (!isRecyclable && recyclable){
        setIsRecyclable(recyclable)
    }
}