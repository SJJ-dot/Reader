package com.sjianjun.reader

import android.content.Context
import android.util.Log
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions

@GlideModule
class MyAppGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
//        builder.setLogLevel(Log.VERBOSE )
//        builder.setLogRequestOrigins(true)
    }

    override fun isManifestParsingEnabled(): Boolean {
        return false
    }
}