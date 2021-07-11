package com.sjianjun.reader.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

abstract class BaseAdapter<T>(val itemRes: Int = 0) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var data = mutableListOf<T>()

    override fun getItemCount() = data.size

    @Deprecated("use itemLayoutRes", ReplaceWith("itemLayoutRes"))
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return object : RecyclerView.ViewHolder(
            LayoutInflater.from(parent.context).inflate(
                itemLayoutRes(viewType),
                parent,
                false
            )
        ) {
        }
    }

    protected open fun itemLayoutRes(viewType: Int): Int {
        return itemRes
    }

}