package com.sjianjun.reader.preferences

import android.content.Context
import com.sjianjun.reader.App
import com.sjianjun.reader.view.CustomWebView

val adBlockConfig by lazy { AdBlock("adBlockList") }

class AdBlock(val name: String) :
    DelegateSharedPref(App.app.getSharedPreferences("AppConfig_$name", Context.MODE_PRIVATE)) {
    /**
     * 需要拦截的广告SDK url
     */
    var adBlockList by dataPref<List<CustomWebView.AdBlock>>(
        "adBlockList",
        emptyList<CustomWebView.AdBlock>()
    )


    var adBlockUrlListVersion by intPref("adBlockUrlListVersion", 0)
}