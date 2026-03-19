package com.sjianjun.reader.module.feedback

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.reader.R
import com.sjianjun.reader.mqtt.Feedback
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.gone
import com.sjianjun.reader.utils.show
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FeedbackAdapter(
    private val onReply: (Feedback) -> Unit,
    private val onDelete: (Feedback) -> Unit
) : RecyclerView.Adapter<FeedbackAdapter.VH>() {

    private val list = mutableListOf<Feedback>()

    fun updateList(newList: List<Feedback>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_feedback, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(list[position])
        holder.itemView.findViewById<View>(R.id.btn_reply).setOnClickListener {
            onReply(list[position])
        }
        holder.itemView.findViewById<View>(R.id.btn_delete).setOnClickListener {
            onDelete(list[position])
        }

        if (globalConfig.admin || list[position].clientId == globalConfig.mqttClientId) {
            holder.itemView.findViewById<View>(R.id.btn_delete).show()
        } else {
            holder.itemView.findViewById<View>(R.id.btn_delete).gone()
        }

        if (globalConfig.admin) {
            holder.itemView.findViewById<View>(R.id.tv_reply).show()
        } else {
            holder.itemView.findViewById<View>(R.id.tv_reply).gone()
        }
    }

    override fun getItemCount(): Int = list.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvContent: TextView = itemView.findViewById(R.id.tv_content)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_time)
        private val tvReply: TextView = itemView.findViewById(R.id.tv_reply)

        private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        fun bind(f: Feedback) {
            tvContent.text = f.content ?: ""
            tvTime.text = sdf.format(Date(f.timestamp))
            tvReply.visibility = if (f.reply.isNullOrEmpty()) View.GONE else View.VISIBLE
            tvReply.text = f.reply ?: ""
        }
    }

}

