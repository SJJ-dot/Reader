package com.sjianjun.reader.utils

import android.app.Activity
import android.content.Context
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.sjianjun.reader.R


val requestOptions by lazy { RequestOptions.placeholderOf(R.mipmap.ic_xue_xi).centerCrop() }

/**
 * @param host fragment or activity
 */
fun ImageView.glide(host: Any, url: String?) {
    val requestManager = when (host) {
        is Fragment -> Glide.with(host)
        is FragmentActivity -> Glide.with(host)
        is Activity -> Glide.with(host)
        is android.app.Fragment -> Glide.with(host)
        is Context -> Glide.with(host)
        else -> Glide.with(this)
    }
    requestManager
        .applyDefaultRequestOptions(requestOptions)
        .load(url)
        .into(this)
}