package com.sjianjun.reader.view

import androidx.appcompat.widget.LinearLayoutCompat
import com.sjianjun.reader.databinding.AppUpdateDialogUpdateBinding
import com.sjianjun.reader.preferences.appEssentials
import com.sjianjun.reader.utils.UpdateSettleBridge
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
            // 关闭弹窗：触发收尾回调（进入主页/退出更新进程），并关闭库的弹窗 Activity
            UpdateSettleBridge.fire()
            activity?.finish()
        }
        binding.tvIgnore.click {
            appEssentials.releasesInfo?.lastVersion?.let {
                appEssentials.ignoredUpdateVersion = it
            }
            // 忽略本次更新：同样触发收尾回调，并关闭库的弹窗 Activity
            UpdateSettleBridge.fire()
            activity?.finish()
        }
    }
}
