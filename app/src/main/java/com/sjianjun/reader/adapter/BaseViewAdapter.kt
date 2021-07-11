package com.sjianjun.reader.adapter

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

abstract class BaseViewAdapter<T, V : View>(var data: MutableList<T> = mutableListOf()) :
    RecyclerView.Adapter<BaseViewAdapter.VH<V>>() {

    override fun getItemCount() = data.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH<V> {
        return VH(createView(parent, viewType))
    }

    protected abstract fun createView(parent: ViewGroup, viewType: Int): V

    class VH<V : View>(val itemV: V) : RecyclerView.ViewHolder(itemV)
}