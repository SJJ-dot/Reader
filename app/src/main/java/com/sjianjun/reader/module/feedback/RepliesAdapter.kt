package com.sjianjun.reader.module.feedback

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.reader.R
import com.sjianjun.reader.mqtt.Reply
import com.sjianjun.reader.preferences.globalConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RepliesAdapter(private val onLongDelete: ((Int) -> Unit)? = null) : RecyclerView.Adapter<RepliesAdapter.VH>() {

    private val list = mutableListOf<Reply>()

    fun submitList(newList: List<Reply>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_reply, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(list[position])
        if (globalConfig.admin || list[position].author == globalConfig.mqttClientId) {
            holder.itemView.setOnLongClickListener {
                onLongDelete?.invoke(position)
                true
            }
        } else {
            holder.itemView.setOnLongClickListener(null)
        }

    }

    override fun getItemCount(): Int = list.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAuthor: TextView = itemView.findViewById(R.id.tv_reply_author)
        private val tvContent: TextView = itemView.findViewById(R.id.tv_reply_content)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_reply_time)

        private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        fun bind(r: Reply) {
            val author = if (r.author == globalConfig.mqttClientId) "我" else "书友"
            tvAuthor.text = author
            tvContent.text = r.content ?: ""
            tvTime.text = sdf.format(Date(r.timestamp))
        }
    }

}


