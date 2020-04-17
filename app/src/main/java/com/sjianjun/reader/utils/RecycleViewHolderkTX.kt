package com.sjianjun.reader.utils

import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.reader.R

fun RecyclerView.ViewHolder.setRecyclable(recyclable: Boolean) {
    if (itemView.getTag(R.id.recyclable) != false && !recyclable) {
        setIsRecyclable(false)
        itemView.setTag(R.id.recyclable, false)
    } else if (itemView.getTag(R.id.recyclable) == false && recyclable) {
        itemView.setTag(R.id.recyclable, true)
        setIsRecyclable(true)
    }
}