package com.sjianjun.reader.module.reader

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.DialogTtsTimerBinding
import com.sjianjun.reader.tts.TtsPlaybackController

/**
 * 定时停止设置：选择定时时长、选择是否读完当前章节再停。
 * 选项即时生效，不需要确认按钮。
 */
class TtsTimerDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogTtsTimerBinding.inflate(layoutInflater)
        val state = TtsPlaybackController.state.value
        val minutes = state?.sleepTimerMinutes ?: 0
        // 先按当前设置回显，再挂监听，避免回显时触发一次设置
        binding.timerGroup.check(
            when (minutes) {
                15 -> R.id.timer_15
                30 -> R.id.timer_30
                60 -> R.id.timer_60
                else -> R.id.timer_off
            }
        )
        binding.stopAfterChapter.isChecked = state?.stopAfterChapter == true

        binding.timerGroup.setOnCheckedChangeListener { _, checkedId ->
            val selected = when (checkedId) {
                R.id.timer_15 -> 15
                R.id.timer_30 -> 30
                R.id.timer_60 -> 60
                else -> 0
            }
            TtsPlaybackController.setSleepTimer(selected)
        }
        binding.stopAfterChapter.setOnCheckedChangeListener { _, checked ->
            TtsPlaybackController.setStopAfterChapter(checked)
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("定时停止")
            .setView(binding.root)
            .setPositiveButton("完成", null)
            .create()
    }

    companion object {
        const val TAG = "TtsTimerDialogFragment"
    }
}
