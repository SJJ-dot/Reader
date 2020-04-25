package com.sjianjun.reader.popup

import android.content.Context
import android.view.View
import com.sjianjun.reader.R
import kotlinx.android.synthetic.main.pop_error_msg.view.*
import razerdp.basepopup.BasePopupWindow

class ErrorMsgPopup(context: Context?) : BasePopupWindow(context) {
    override fun onCreateContentView(): View {
        return createPopupById(R.layout.pop_error_msg)
    }

    fun init(msg: String?): ErrorMsgPopup {
        contentView?.apply {
            text.text = msg
        }
        return this
    }
}