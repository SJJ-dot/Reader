package com.sjianjun.reader.popup

import android.content.Context
import com.sjianjun.reader.R
import kotlinx.android.synthetic.main.pop_error_msg.view.text
import razerdp.basepopup.BasePopupWindow

class ErrorMsgPopup(context: Context?) : BasePopupWindow(context) {
    fun init(msg: String?): ErrorMsgPopup {
        setContentView(R.layout.pop_error_msg)
        contentView?.apply {
            text.text = msg
        }
        return this
    }
}