package com.sjianjun.reader.module.reader

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioButton
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

        // restore selection from globalConfig
        val current = globalConfig.readerTtsEngine.value ?: "default"
        if (current == "edge") rbEdge.isChecked = true else rbDefault.isChecked = true

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

            // notify to start reading
            EventBus.post(EventKey.CHAPTER_SPEAK)
            dialog.dismiss()
        }

        return dialog
    }
}


