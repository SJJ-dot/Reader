package com.sjianjun.reader.module.feedback

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.reader.R
import com.sjianjun.reader.mqtt.Feedback
import com.sjianjun.reader.preferences.globalConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RepliesAdapter(val list: List<Feedback>, private val onLongDelete: ((Feedback) -> Unit)? = null) : RecyclerView.Adapter<RepliesAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_reply, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(list[position])
        if (globalConfig.admin || list[position].client_id == globalConfig.mqttClientId) {
            holder.itemView.setOnLongClickListener {
                onLongDelete?.invoke(list[position])
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

        fun bind(r: Feedback) {
            val author = if (r.client_id == globalConfig.mqttClientId) "我" else "书友"
            tvAuthor.text = author
            tvContent.text = r.content ?: ""
            tvTime.text = sdf.format(Date(r.created_at * 1000))
        }
    }

}


