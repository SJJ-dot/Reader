package com.sjianjun.reader.module.feedback

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.FragmentFeedbackFragmentBinding
import com.sjianjun.reader.mqtt.Feedbacks
import com.sjianjun.reader.preferences.globalConfig


class FeedbackFragment : BaseFragment() {

    private var binding: FragmentFeedbackFragmentBinding? = null

    private lateinit var adapter: FeedbackAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Use view binding to inflate layout
        val binding = FragmentFeedbackFragmentBinding.inflate(inflater, container, false)
        this.binding = binding
        adapter = FeedbackAdapter(onReply = { feedback ->
            showReplyDialog(feedback)
        }, onDelete = { feedback ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除反馈")
                .setMessage("确定删除该反馈吗？")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    Feedbacks.deleteFeedback(feedback)
                }
                .show()
        })

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        // observe feedback map and update list sorted by timestamp desc
        Feedbacks.feedbackMap.observeViewLifecycle { map ->
            val list = map.values.toList().sortedByDescending { it.timestamp }
            adapter.updateList(list)
        }

        binding.fab.setOnClickListener {
            showSendDialog()
        }
        Feedbacks.subscribe()
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Feedbacks.unsubscribe()
    }

    private fun showSendDialog() {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_feedback, null)
        val editText = view.findViewById<EditText>(R.id.et_feedback)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.feedback_title)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val text = editText.text.toString().trim()
                if (text == BuildConfig.MQTT_PASSWORD){
                    globalConfig.admin = !globalConfig.admin
                    adapter.notifyDataSetChanged()
                    return@setPositiveButton
                }
                if (text.isNotEmpty()) {
                    Feedbacks.sendFeedback(text)
                }
            }
            .show()
    }

    private fun showReplyDialog(feedback: com.sjianjun.reader.mqtt.Feedback) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_feedback, null)
        val editText = view.findViewById<EditText>(R.id.et_feedback)
        editText.hint = "回复内容"

        AlertDialog.Builder(requireContext())
            .setTitle("回复")
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val text = editText.text.toString().trim()
                if (text.isNotEmpty()) {
                    feedback.reply = text
                    feedback.repliedAt = System.currentTimeMillis()
                    Feedbacks.appendReply(feedback, text)
                }
            }
            .show()
    }

}