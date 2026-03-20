package com.sjianjun.reader.module.feedback

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.R
import com.sjianjun.reader.mqtt.Feedback
import com.sjianjun.reader.mqtt.Feedbacks
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

    // track expanded feedback ids
    private val expanded = mutableSetOf<String>()

    fun updateList(newList: List<Feedback>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_feedback, parent, false)
        return VH(v)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: VH, position: Int) {
        val feedback = list[position]
        holder.bind(feedback)

        holder.itemView.findViewById<View>(R.id.btn_reply).setOnClickListener { onReply(feedback) }
        holder.itemView.findViewById<View>(R.id.btn_delete).setOnClickListener { onDelete(feedback) }

        if (globalConfig.admin || feedback.clientId == globalConfig.mqttClientId) {
            holder.itemView.findViewById<View>(R.id.btn_delete).show()
        } else {
            holder.itemView.findViewById<View>(R.id.btn_delete).gone()
        }
        if (globalConfig.admin || feedback.clientId == globalConfig.mqttClientId) {
            holder.itemView.findViewById<View>(R.id.btn_reply).show()
        } else {
            holder.itemView.findViewById<View>(R.id.btn_reply).gone()
        }

        val rvReplies = holder.itemView.findViewById<RecyclerView>(R.id.rv_replies)
        val tvToggle = holder.itemView.findViewById<TextView>(R.id.tv_toggle_replies)
        val tvLatest = holder.itemView.findViewById<TextView>(R.id.tv_latest_reply)
        val tvLatestTime = holder.itemView.findViewById<TextView>(R.id.tv_latest_reply_time)
        if (rvReplies != null && tvToggle != null) {
            if (rvReplies.adapter == null) {
                rvReplies.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(holder.itemView.context)
                rvReplies.adapter = RepliesAdapter { idx ->
                    // long press delete callback
                    val ctx = holder.itemView.context
                    showDeleteDialog(feedback, idx, ctx)
                }
            }
            (rvReplies.adapter as RepliesAdapter).submitList(feedback.replies)
            val isExpanded = expanded.contains(feedback.id)
            rvReplies.visibility = if (isExpanded) View.VISIBLE else View.GONE
            tvToggle.text = if (isExpanded) "收起回复" else "展开回复"
            // show latest reply preview when collapsed
            tvLatest.setOnClickListener(null)
            if (!isExpanded && feedback.replies.isNotEmpty()) {
                val latest = feedback.replies.last()
                val author = if (latest.author.isNullOrBlank()) "无名氏" else if (latest.author == globalConfig.mqttClientId) "我" else "回复"
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                tvLatest?.visibility = View.VISIBLE
                tvLatest?.text = "${author}: ${latest.content}"
                tvLatestTime?.visibility = View.VISIBLE
                tvLatestTime?.text = sdf.format(Date(latest.timestamp))
                if (globalConfig.admin || latest.author == globalConfig.mqttClientId) {
                    tvLatest.setOnLongClickListener {
                        // long press delete callback
                        val ctx = holder.itemView.context
                        showDeleteDialog(feedback, feedback.replies.size - 1, ctx)
                        true
                    }
                }
            } else {
                tvLatest?.visibility = View.GONE
                tvLatestTime?.visibility = View.GONE
            }
            if (feedback.replies.isEmpty()) {
                tvToggle.visibility = View.GONE
                tvLatest?.visibility = View.GONE
                tvLatestTime?.visibility = View.GONE
            } else {
                tvToggle.visibility = View.VISIBLE
                tvToggle.setOnClickListener {
                    if (isExpanded) expanded.remove(feedback.id) else expanded.add(feedback.id)
                    notifyItemChanged(position)
                }
            }
        }
    }

    private fun showDeleteDialog(feedback: Feedback, idx: Int, ctx: Context) {
        MaterialAlertDialogBuilder(ctx)
            .setTitle("删除回复")
            .setMessage("确定删除该回复吗？")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                Feedbacks.deleteReply(feedback, idx)
            }
            .show()
    }

    override fun getItemCount(): Int = list.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvContent: TextView = itemView.findViewById(R.id.tv_content)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_time)

        private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        fun bind(f: Feedback) {
            tvContent.text = f.content ?: ""
            tvTime.text = sdf.format(Date(f.timestamp))
            // replies shown in nested RecyclerView
        }
    }

}

