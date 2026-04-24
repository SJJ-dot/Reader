package com.sjianjun.reader.module.feedback

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.ItemFeedbackBinding
import com.sjianjun.reader.mqtt.Feedback
import com.sjianjun.reader.mqtt.Feedbacks
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.gone
import com.sjianjun.reader.utils.show
import kotlinx.coroutines.launch
import sjj.alog.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FeedbackAdapter(
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onReply: (Feedback) -> Unit,
    private val onDelete: (Feedback) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val list = mutableListOf<Feedback>()

    // track expanded feedback ids
    private val expanded = mutableSetOf<String>()

    fun updateList(newList: List<Feedback>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_feedback, parent, false)
        return object : RecyclerView.ViewHolder(v) {}
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val feedback = list[position]
        val binding = ItemFeedbackBinding.bind(holder.itemView)
        binding.tvContent.text = feedback.content ?: ""
        binding.tvTime.text = sdf.format(Date(feedback.created_at * 1000))

        binding.btnReply.setOnClickListener { onReply(feedback) }
        binding.btnDelete.setOnClickListener { onDelete(feedback) }

        if (globalConfig.admin || feedback.client_id == globalConfig.mqttClientId) {
            binding.btnReply.show()
            binding.btnDelete.show()
        } else {
            binding.btnReply.gone()
            binding.btnDelete.gone()
        }


        binding.rvReplies.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(holder.itemView.context)
        binding.rvReplies.adapter = RepliesAdapter(feedback.replies?.sortedBy { it.created_at } ?: emptyList()) { f ->
            // long press delete callback
            val ctx = holder.itemView.context
            showDeleteDialog(f, ctx)
        }
        if (feedback.replies.isNullOrEmpty()) {
            binding.tvToggleReplies.gone()
            binding.tvLatestReply.gone()
            binding.tvLatestReplyTime.gone()
            binding.rvReplies.gone()
        } else if (feedback.replies?.size == 1) {
            binding.tvToggleReplies.gone()
            binding.rvReplies.gone()
            binding.tvLatestReply.show()
            binding.tvLatestReplyTime.show()
            val latest = feedback.replies?.last()!!
            val author = if (latest.client_id == globalConfig.mqttClientId) "我" else "书友"
            binding.tvLatestReply.text = "${author}: ${latest.content}"
            binding.tvLatestReplyTime.text = sdf.format(Date(latest.created_at * 1000))
        } else {
            val isExpanded = expanded.contains(feedback.id)
            if (isExpanded) {
                binding.rvReplies.show()
                binding.tvLatestReply.gone()
                binding.tvLatestReplyTime.gone()
            } else {
                binding.rvReplies.gone()
                binding.tvLatestReply.show()
                binding.tvLatestReplyTime.show()
            }
            binding.tvToggleReplies.show()
            val latest = feedback.replies?.last()!!
            val author = if (latest.client_id == globalConfig.mqttClientId) "我" else "书友"
            binding.tvLatestReply.text = "${author}: ${latest.content}"
            binding.tvLatestReplyTime.text = sdf.format(Date(latest.created_at * 1000))
            binding.tvToggleReplies.text = if (isExpanded) "收起回复" else "展开回复"
        }
        val isExpanded = expanded.contains(feedback.id)
        binding.tvToggleReplies.setOnClickListener {
            if (isExpanded) expanded.remove(feedback.id) else expanded.add(feedback.id)
            notifyItemChanged(position)
        }
        binding.tvLatestReply.setOnLongClickListener {
            // long press delete callback
            val latest = feedback.replies?.last()!!
            val ctx = holder.itemView.context
            showDeleteDialog(feedback, ctx)
            true
        }
    }

    private fun showDeleteDialog(feedback: Feedback, ctx: Context) {
        MaterialAlertDialogBuilder(ctx)
            .setTitle("删除回复")
            .setMessage("确定删除该回复吗？")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    Feedbacks.deleteReply(feedback)
                }
            }
            .show()
    }

    override fun getItemCount(): Int = list.size


}

