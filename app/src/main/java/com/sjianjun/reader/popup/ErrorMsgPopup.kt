package com.sjianjun.reader.popup

import android.content.Context
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.PopErrorMsgBinding
import razerdp.basepopup.BasePopupWindow

class ErrorMsgPopup(context: Context?) : BasePopupWindow(context) {
    fun init(msg: String?): ErrorMsgPopup {

        setContentView(R.layout.pop_error_msg)
        val binding = PopErrorMsgBinding.bind(contentView)
        binding.text.text = msg
        return this
    }
}