package com.sjianjun.reader.utils

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.drawable.DrawableCompat
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.sjianjun.reader.App
import com.sjianjun.reader.GlideApp
import com.sjianjun.reader.R
import com.sjianjun.reader.preferences.globalConfig


/**
 * @param host fragment or activity
 */
fun ImageView.glide(url: String?) {
    GlideApp.with(this)
        .applyDefaultRequestOptions(RequestOptions.placeholderOf(R.mipmap.ic_xue_xi).centerCrop())
        .load(url)
        .listener(object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>,
                isFirstResource: Boolean
            ): Boolean {
                return false
            }

            override fun onResourceReady(
                resource: Drawable,
                model: Any,
                target: Target<Drawable>?,
                dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean {
                if (globalConfig.appDayNightMode == AppCompatDelegate.MODE_NIGHT_YES) {
                    val wrappedDrawable = DrawableCompat.wrap(resource)
                    val colorMatrix = ColorMatrix()
                    colorMatrix.setScale(0.5f, 0.5f, 0.5f, 1.0f)
                    val filter = ColorMatrixColorFilter(colorMatrix)
                    wrappedDrawable.colorFilter = filter
                    setImageDrawable(wrappedDrawable)
                } else {
                    setImageDrawable(resource)
                }

                return true
            }
        })
        .into(this)
}