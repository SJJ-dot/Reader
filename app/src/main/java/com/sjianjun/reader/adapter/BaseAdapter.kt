package com.sjianjun.reader.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class DiffCallback<T>(val oldData: List<T>, val newData: List<T>, val id: (o: T, n: T) -> Boolean) : androidx.recyclerview.widget.DiffUtil.Callback() {
    override fun getOldListSize() = oldData.size
    override fun getNewListSize() = newData.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return id(oldData[oldItemPosition], newData[newItemPosition])
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldData[oldItemPosition] == newData[newItemPosition]
    }
}

abstract class BaseAdapter<T>(val itemRes: Int = 0) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
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

    fun notifyDataSetDiff(newData: List<T>, id: (o: T, n: T) -> Boolean) {
        val diffCallback = DiffCallback<T>(data, newData, id)
        // 计算差异
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(diffCallback)
        // 更新数据
        data.clear()
        data.addAll(newData)
        // 通知 RecyclerView 更新
        diffResult.dispatchUpdatesTo(this)
    }


}