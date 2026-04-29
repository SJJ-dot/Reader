package com.sjianjun.reader.module.feedback

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.R
import com.sjianjun.reader.mqtt.Feedback
import com.sjianjun.reader.mqtt.user
import com.sjianjun.reader.utils.toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RepliesAdapter(
    val list: List<Feedback>,
    private val canDelete: (Feedback) -> Boolean = { false },
    private val onDelete: ((Feedback) -> Unit)? = null,
) : RecyclerView.Adapter<RepliesAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_reply, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val reply = list[position]
        holder.bind(reply)
        holder.itemView.setOnLongClickListener {
            showReplyActionDialog(holder.itemView.context, reply)
            true
        }
    }

    override fun getItemCount(): Int = list.size

    private fun showReplyActionDialog(ctx: Context, reply: Feedback) {
        val content = reply.content.orEmpty()
        if (content.isBlank() && !canDelete(reply)) {
            return
        }
        val options = buildList {
            if (content.isNotBlank()) {
                add("复制内容")
            }
            if (canDelete(reply)) {
                add("删除回复")
            }
        }
        MaterialAlertDialogBuilder(ctx)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "复制内容" -> copyToClipboard(ctx, content)
                    "删除回复" -> onDelete?.invoke(reply)
                }
            }
            .show()
    }

    private fun copyToClipboard(ctx: Context, content: String) {
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("reply", content))
        toast("已复制")
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAuthor: TextView = itemView.findViewById(R.id.tv_reply_author)
        private val tvContent: TextView = itemView.findViewById(R.id.tv_reply_content)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_reply_time)

        private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        fun bind(r: Feedback) {
            tvAuthor.text = r.client_id.user
            tvContent.text = r.content ?: ""
            tvTime.text = sdf.format(Date(r.created_at * 1000))
        }
    }

}


