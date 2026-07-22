package com.sjianjun.reader.view

import androidx.appcompat.widget.LinearLayoutCompat
import com.sjianjun.reader.databinding.AppUpdateDialogUpdateBinding
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.gone
import com.sjianjun.reader.utils.activity

class AppUpdateDialogUpdate : LinearLayoutCompat {
    constructor(context: android.content.Context) : super(context)
    constructor(context: android.content.Context, attrs: android.util.AttributeSet?) : super(context, attrs)
    constructor(context: android.content.Context, attrs: android.util.AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val binding = AppUpdateDialogUpdateBinding.bind(this)
        binding.ibClose.gone()
        binding.ibClose2.click {
            activity?.finish()
        }
        binding.tvIgnore.click {
            globalConfig.releasesInfo?.lastVersion?.let {
                globalConfig.ignoredUpdateVersion = it
            }
            activity?.finish()
        }
    }
}
