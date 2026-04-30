package com.sjianjun.reader.module.feedback

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.ItemFeedbackBinding
import com.sjianjun.reader.mqtt.Feedback
import com.sjianjun.reader.mqtt.Feedbacks
import com.sjianjun.reader.mqtt.user
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.gone
import com.sjianjun.reader.utils.show
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FeedbackAdapter(
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onReply: (Feedback) -> Unit,
    private val onDelete: (Feedback) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private companion object {
        const val COLLAPSED_MAX_LINES = 3
        const val EXPAND_HINT = "点击展开"
        const val COLLAPSE_HINT = "点击收起"
    }

    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val list = mutableListOf<Feedback>()

    // track expanded reply list ids
    private val expanded = mutableSetOf<String>()
    private val expandedContent = mutableSetOf<String>()
    private val expandedLatestReply = mutableSetOf<String>()

    fun updateList(newList: List<Feedback>) {
        val validIds = newList.map { it.id }.toSet()
        expanded.retainAll(validIds)
        expandedContent.retainAll(validIds)
        expandedLatestReply.retainAll(validIds)
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
        bindExpandableText(
            textView = binding.tvContent,
            text = feedback.content.orEmpty(),
            itemId = feedback.id,
            expandedState = expandedContent,
        )
        binding.tvContent.setOnLongClickListener {
            showContentActionDialog(feedback.content.orEmpty(), holder.itemView.context)
            true
        }
        binding.tvTime.text = "${feedback.client_id.user} " + sdf.format(Date(feedback.created_at * 1000))

        binding.btnReply.setOnClickListener { onReply(feedback) }
        binding.btnDelete.setOnClickListener { onDelete(feedback) }

        if (canDelete(feedback)) {
            binding.btnDelete.show()
        } else {
            binding.btnDelete.gone()
        }


        val replies = feedback.replies?.sortedByDescending { it.created_at } ?: emptyList()
        val latestReply = replies.firstOrNull()

        binding.rvReplies.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(holder.itemView.context)
        binding.rvReplies.adapter = RepliesAdapter(
            list = replies,
            canDelete = { reply -> canDelete(reply) },
            onDelete = { reply ->
                val ctx = holder.itemView.context
                showDeleteDialog(reply, ctx)
            },
        )
        if (replies.isEmpty()) {
            binding.tvToggleReplies.gone()
            binding.tvLatestReply.gone()
            binding.tvLatestReplyTime.gone()
            binding.rvReplies.gone()
        } else if (replies.size == 1) {
            binding.tvToggleReplies.gone()
            binding.rvReplies.gone()
            binding.tvLatestReply.show()
            binding.tvLatestReplyTime.show()
            bindExpandableText(
                textView = binding.tvLatestReply,
                text = latestReply?.content.orEmpty(),
                itemId = feedback.id,
                expandedState = expandedLatestReply,
            )
            binding.tvLatestReplyTime.text ="${latestReply?.client_id?.user.orEmpty()} " + sdf.format(Date((latestReply?.created_at ?: 0) * 1000))
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
            bindExpandableText(
                textView = binding.tvLatestReply,
                text = latestReply?.content.orEmpty(),
                itemId = feedback.id,
                expandedState = expandedLatestReply,
            )
            binding.tvLatestReplyTime.text ="${latestReply?.client_id?.user.orEmpty()} " + sdf.format(Date((latestReply?.created_at ?: 0) * 1000))
            binding.tvToggleReplies.text = if (isExpanded) "收起回复" else "展开回复"
        }
        val isExpanded = expanded.contains(feedback.id)
        binding.tvToggleReplies.setOnClickListener {
            if (isExpanded) expanded.remove(feedback.id) else expanded.add(feedback.id)
            notifyItemChanged(position)
        }
        binding.tvLatestReply.setOnLongClickListener {
            val latest = latestReply ?: return@setOnLongClickListener true
            val ctx = holder.itemView.context
            showReplyActionDialog(latest, ctx)
            true
        }
    }

    private fun canDelete(feedback: Feedback): Boolean {
        return globalConfig.admin || feedback.client_id == globalConfig.mqttClientId
    }

    private fun bindExpandableText(
        textView: TextView,
        text: String,
        itemId: String,
        expandedState: MutableSet<String>,
    ) {
        textView.tag = itemId to text
        textView.maxLines = Int.MAX_VALUE
        textView.text = text
        textView.setOnClickListener(null)

        if (text.isBlank()) {
            return
        }

        textView.post {
            val tagValue = textView.tag as? Pair<*, *> ?: return@post
            if (tagValue.first != itemId || tagValue.second != text) {
                return@post
            }
            val layout = textView.layout ?: return@post
            val isExpandable = layout.lineCount > COLLAPSED_MAX_LINES
            if (!isExpandable) {
                textView.maxLines = Int.MAX_VALUE
                textView.text = text
                textView.setOnClickListener(null)
                return@post
            }

            if (expandedState.contains(itemId)) {
                textView.maxLines = Int.MAX_VALUE
                textView.text = buildExpandedText(textView, text)
                textView.setOnClickListener {
                    expandedState.remove(itemId)
                    bindExpandableText(textView, text, itemId, expandedState)
                }
                return@post
            }

            val collapsedText = buildCollapsedText(textView, text, layout)
            textView.maxLines = COLLAPSED_MAX_LINES
            textView.text = collapsedText
            textView.setOnClickListener {
                expandedState.add(itemId)
                bindExpandableText(textView, text, itemId, expandedState)
            }
        }
    }

    private fun buildExpandedText(textView: TextView, text: String): CharSequence {
        val expanded = SpannableStringBuilder(text)
        if (expanded.isNotEmpty()) {
            expanded.append(" ")
        }
        val hintStart = expanded.length
        expanded.append(COLLAPSE_HINT)
        expanded.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(textView.context, R.color.dn_color_primary)),
            hintStart,
            expanded.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        expanded.setSpan(
            StyleSpan(Typeface.BOLD),
            hintStart,
            expanded.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        return expanded
    }

    private fun buildCollapsedText(
        textView: TextView,
        text: String,
        layout: android.text.Layout,
    ): CharSequence {
        val lineIndex = COLLAPSED_MAX_LINES - 1
        val lineStart = layout.getLineStart(lineIndex)
        val lineEnd = layout.getLineEnd(lineIndex).coerceAtMost(text.length)
        val prefix = text.substring(0, lineStart)
        var lineText = text.substring(lineStart, lineEnd).trimEnd()
        val maxWidth = (textView.width - textView.paddingLeft - textView.paddingRight).toFloat().coerceAtLeast(0f)
        val suffix = "…$EXPAND_HINT"

        while (lineText.isNotEmpty() && textView.paint.measureText(lineText + suffix) > maxWidth) {
            lineText = lineText.dropLast(1).trimEnd()
        }

        val collapsed = SpannableStringBuilder(prefix)
            .append(lineText)
            .append("…")
        val hintStart = collapsed.length
        collapsed.append(EXPAND_HINT)
        collapsed.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(textView.context, R.color.dn_color_primary)),
            hintStart,
            collapsed.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        collapsed.setSpan(
            StyleSpan(Typeface.BOLD),
            hintStart,
            collapsed.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        return collapsed
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

    private fun showContentActionDialog(content: String, ctx: Context) {
        if (content.isBlank()) {
            return
        }
        MaterialAlertDialogBuilder(ctx)
            .setItems(arrayOf("复制内容")) { _, which ->
                if (which == 0) {
                    copyToClipboard(content, ctx)
                }
            }
            .show()
    }

    private fun showReplyActionDialog(reply: Feedback, ctx: Context) {
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
                    "复制内容" -> copyToClipboard(content, ctx)
                    "删除回复" -> showDeleteDialog(reply, ctx)
                }
            }
            .show()
    }

    private fun copyToClipboard(content: String, ctx: Context) {
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("feedback", content))
        toast("已复制")
    }

    override fun getItemCount(): Int = list.size


}

