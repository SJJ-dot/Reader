package com.sjianjun.reader.module.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.DialogEditTextBinding
import com.sjianjun.reader.databinding.FragmentSettingsBinding
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.HttpServiceHelper
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.click
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log

/**
 * A simple [Fragment] subclass.
 * Use the [SettingsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class SettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        FragmentSettingsBinding.bind(view).apply {

            HttpServiceHelper.isRunning.observe(viewLifecycleOwner) {
                debugServiceSwitch.isChecked = it
            }
            debugServiceSwitch.setOnCheckedChangeListener { btn, isChecked ->
                if (isChecked){
                    if (HttpServiceHelper.isRunning.value != true){
                        HttpServiceHelper.startHttpServer()
                    }
                }else{
                    HttpServiceHelper.stopHttpServer()
                }
            }

        }
    }

}