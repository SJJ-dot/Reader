package com.sjianjun.reader.module.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.fragment.app.DialogFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.URL_REPO
import com.sjianjun.reader.databinding.FragmentPrivacyPolicyDialogBinding
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.ActivityManger
import com.sjianjun.reader.utils.colorText
import com.sjianjun.reader.utils.htmlToSpanned

class PrivacyPolicyDialogFragment : DialogFragment() {
    private var binding: FragmentPrivacyPolicyDialogBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentPrivacyPolicyDialogBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        isCancelable = false
        binding?.apply {
            val htmlMessage = getString(R.string.privacy_policy_dialog_message, URL_REPO)
                .replace("\n", "<br/>")
                .colorText("同意并继续", "#0F9D58".toColorInt())
                .colorText("拒绝并退出", "#DB4437".toColorInt())
            tvContent.text = htmlMessage?.htmlToSpanned()
            agreeButton.setOnClickListener {
                globalConfig.privacyPolicyAccepted.postValue(true)
                dismiss()
            }
            exitButton.setOnClickListener {
                globalConfig.privacyPolicyAccepted.postValue(false)
                ActivityManger.exitApp()
            }
        }
    }
}

