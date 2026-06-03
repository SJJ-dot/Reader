package com.sjianjun.reader.module.reader

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioButton
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.preferences.globalConfig

class SettingTtsDialogFragment : DialogFragment() {
    companion object {
        const val TAG = "SettingTtsDialogFragment"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view: View = layoutInflater.inflate(R.layout.fragment_setting_tts, null)
        val rbDefault = view.findViewById<RadioButton>(R.id.rb_tts_default)
        val rbEdge = view.findViewById<RadioButton>(R.id.rb_tts_edge)
        val spinner = view.findViewById<Spinner>(R.id.spinner_tts_voice)

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
            "小北（辽宁方言）@zh-CN-liaoning-XiaobeiNeural",
            "小妮（陕西方言）@zh-CN-shaanxi-XiaoniNeural"
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
        // only show spinner when Edge is selected
        spinner.visibility = if (rbEdge.isChecked) View.VISIBLE else View.GONE
        rbEdge.setOnCheckedChangeListener { _, isChecked ->
            spinner.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        rbDefault.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) spinner.visibility = View.GONE
        }

        val btnCancel = view.findViewById<Button>(R.id.btn_tts_cancel)
        val btnPlay = view.findViewById<Button>(R.id.btn_tts_play)

        val builder = AlertDialog.Builder(requireContext())
        builder.setView(view)

        val dialog = builder.create()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnPlay.setOnClickListener {
            // save selection
            val engine = if (rbEdge.isChecked) "edge" else "default"
            globalConfig.readerTtsEngine.postValue(engine)
            // save voice param for edge
            val selIdx = spinner.selectedItemPosition.coerceAtLeast(0)
            val voiceParam = voiceParams.getOrNull(selIdx) ?: "zh-CN-XiaoxiaoNeural"
            globalConfig.readerTtsVoice.postValue(voiceParam)

            // notify to start reading
            EventBus.post(EventKey.CHAPTER_SPEAK)
            dialog.dismiss()
        }

        return dialog
    }
}


