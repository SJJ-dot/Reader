package com.sjianjun.reader.preferences

import com.sjianjun.reader.view.CustomWebView
import com.tencent.mmkv.MMKV

val adBlockConfig by lazy { AdBlock("adBlockList") }

class AdBlock(val name: String) :
    DelegateSharedPref(MMKV.mmkvWithID("AppConfig_$name")) {
    /**
     * 需要拦截的广告SDK url
     */
    var adBlockList by dataPref<List<CustomWebView.AdBlock>>(
        "adBlockList",
        emptyList<CustomWebView.AdBlock>()
    )


    var adBlockUrlListVersion by intPref("adBlockUrlListVersion", 0)
}