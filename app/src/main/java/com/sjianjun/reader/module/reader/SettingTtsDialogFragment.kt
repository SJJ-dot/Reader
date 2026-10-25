package com.sjianjun.reader.module.reader

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.FragmentSettingTtsBinding
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.tts.TtsPlaybackController
import com.sjianjun.reader.tts.v2.TtsVoices
import com.sjianjun.reader.view.click

class SettingTtsDialogFragment : DialogFragment() {
    companion object {
        const val TAG = "SettingTtsDialogFragment"
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentSettingTtsBinding.inflate(layoutInflater) // ensure view binding is generated
        val rbDefault = binding.rbTtsDefault
        val rbEdge = binding.rbTtsEdge
        val spinner = binding.spinnerTtsVoice
        val seekEdge = binding.seekbarEdgeRate
        val tvEdge = binding.tvEdgeRateValue



        // restore selection from globalConfig
        val current = globalConfig.readerTtsEngine.value ?: "default"
        if (current == "edge") rbEdge.isChecked = true else rbDefault.isChecked = true

        // voice options（与字幕浮窗共用同一份列表）
        val voiceDisplay = TtsVoices.displayNames()
        val voiceParams = TtsVoices.params()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, voiceDisplay)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        // restore selected voice param
        val currentVoice = globalConfig.readerTtsVoice.value ?: TtsVoices.defaultParam
        val sel = voiceParams.indexOf(currentVoice).coerceAtLeast(0)
        spinner.setSelection(sel)
        // 播放中打开本弹窗时，切换音色立即生效（不必再点「播放」）
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                voiceParams.getOrNull(position)?.let { TtsPlaybackController.applyVoice(it) }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        // restore system and edge rate settings
        val edgeRate = globalConfig.readerTtsRate.value ?: 50
        // ensure seekbar progress within bounds
        seekEdge.progress = edgeRate.coerceIn(0, 100)
        tvEdge.text = edgeRate.toString()
        // only show spinner when Edge is selected
        spinner.visibility = if (rbEdge.isChecked) View.VISIBLE else View.GONE
        rbEdge.setOnCheckedChangeListener { _, isChecked ->
            spinner.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        rbDefault.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) spinner.visibility = View.GONE
        }

        val btnCancel = binding.btnTtsCancel
        val btnPlay = binding.btnTtsPlay


        btnCancel.setOnClickListener {
            dismissAllowingStateLoss()
        }
        binding.tvRate.click {
            seekEdge.progress = 50
        }

        btnPlay.setOnClickListener {
            // 用 setValue 立即写入（postValue 是异步的，紧接着开始朗读会读到旧值）
            val engine = if (rbEdge.isChecked) "edge" else "default"
            globalConfig.readerTtsEngine.setValue(engine)
            // save voice param for edge
            val selIdx = spinner.selectedItemPosition.coerceAtLeast(0)
            val voiceParam = voiceParams.getOrNull(selIdx) ?: TtsVoices.defaultParam
            globalConfig.readerTtsVoice.setValue(voiceParam)
            // save rate settings
            val newEdge = seekEdge.progress.coerceIn(0, 100)
            globalConfig.readerTtsRate.setValue(newEdge)
            // 立即把语速下发给引擎，避免开始朗读时仍沿用旧语速
            TtsPlaybackController.setRate(newEdge)

            // notify to start reading
            EventBus.post(EventKey.CHAPTER_SPEAK)
            dismissAllowingStateLoss()
        }

        // SeekBar listeners：拖动时若正在朗读，直接实时生效（会话层会从当前段落重读）
        seekEdge.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val p = progress.coerceIn(0, 100)
                tvEdge.text = p.toString()
                if (fromUser && TtsPlaybackController.isActive()) {
                    TtsPlaybackController.setRate(p)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // 松手时再对齐一次，避免节流丢掉最后一次变化
                val p = (seekBar?.progress ?: seekEdge.progress).coerceIn(0, 100)
                if (TtsPlaybackController.isActive()) {
                    TtsPlaybackController.setRate(p)
                }
            }
        })

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val metrics = resources.displayMetrics
        val width = (metrics.widthPixels * 0.96f).toInt()
        dialog?.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
    }
}


