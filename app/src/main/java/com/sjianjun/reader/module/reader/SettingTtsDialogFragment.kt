package com.sjianjun.reader.module.reader

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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

        // voice options: displayName@param
        val voiceOptions = listOf(
            "晓晓@zh-CN-XiaoxiaoNeural",
            "小艺@zh-CN-XiaoyiNeural",
            "云健@zh-CN-YunjianNeural",
            "云希@zh-CN-YunxiNeural",
            "云夏@zh-CN-YunxiaNeural",
            "云扬@zh-CN-YunyangNeural",
            "小北(辽宁方言)@zh-CN-liaoning-XiaobeiNeural",
            "小妮(陕西方言)@zh-CN-shaanxi-XiaoniNeural"
        )
        val voiceDisplay = voiceOptions.map { it.substringBefore("@") }
        val voiceParams = voiceOptions.map { it.substringAfter("@") }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, voiceDisplay)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        // restore selected voice param
        val currentVoice = globalConfig.readerTtsVoice.value ?: "zh-CN-XiaoxiaoNeural"
        val sel = voiceParams.indexOf(currentVoice).coerceAtLeast(0)
        spinner.setSelection(sel)
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
            // save selection
            val engine = if (rbEdge.isChecked) "edge" else "default"
            globalConfig.readerTtsEngine.postValue(engine)
            // save voice param for edge
            val selIdx = spinner.selectedItemPosition.coerceAtLeast(0)
            val voiceParam = voiceParams.getOrNull(selIdx) ?: "zh-CN-XiaoxiaoNeural"
            globalConfig.readerTtsVoice.postValue(voiceParam)
            // save rate settings
            val newEdge = seekEdge.progress.coerceIn(0, 100)
            globalConfig.readerTtsRate.postValue(newEdge)

            // notify to start reading
            EventBus.post(EventKey.CHAPTER_SPEAK)
            dismissAllowingStateLoss()
        }

        // SeekBar listeners
        seekEdge.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val p = progress.coerceIn(0, 100)
                tvEdge.text = p.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
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


