package com.sjianjun.reader.module.reader.style

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import com.sjianjun.reader.R
import com.sjianjun.reader.utils.color
import java.lang.ref.WeakReference

/*
 * Created by shen jian jun on 2020-07-13
 */
enum class PageStyle {
    STYLE_0 {
        override fun getBackground(context: Context): Drawable {
            return ColorDrawable(R.color.dn_reader_content_background.color(context))
        }

        override fun getSpacerColor(context: Context): Int {
            return R.color.spacer_color.color(context)
        }

        override fun getLabelColor(context: Context): Int {
            return R.color.dn_reader_chapter_caption_text_color.color(context)
        }

        override fun getChapterTitleColor(context: Context): Int {
            return R.color.dn_reader_chapter_title_text_color.color(context)
        }

        override fun getChapterContentColor(context: Context): Int {
            return R.color.dn_reader_chapter_content_text_color.color(context)
        }
    },
    STYLE_1 {

        override fun getBackground(context: Context): Drawable {
            return createBackground(context,R.drawable.ic_reader_style1_bg)
        }

        override fun getSpacerColor(context: Context): Int {
            return getLabelColor(context)
        }

        override fun getLabelColor(context: Context): Int {
            return "#666666".color
        }

        override fun getChapterTitleColor(context: Context): Int {
            return "#886A66".color
        }

        override fun getChapterContentColor(context: Context): Int {
            return "#3E3C38".color
        }
    },STYLE_2 {

        override fun getBackground(context: Context): Drawable {
            return createBackground(context,R.drawable.ic_reader_style2_bg)
        }

        override fun getSpacerColor(context: Context): Int {
            return getLabelColor(context)
        }

        override fun getLabelColor(context: Context): Int {
            return "#BDBDBD".color
        }

        override fun getChapterTitleColor(context: Context): Int {
            return "#BDBDBD".color
        }

        override fun getChapterContentColor(context: Context): Int {
            return "#999999".color
        }
    };

    abstract fun getBackground(context: Context): Drawable

    private var _background:WeakReference<Drawable?>? = null
    fun createBackground(context: Context,resId:Int): Drawable {
        var background = _background?.get()
        if (background == null) {
            val bitmap = BitmapFactory
                .decodeResource(context.resources, resId)

            val drawable = BitmapDrawable(context.resources, bitmap)
            drawable.tileModeY = Shader.TileMode.MIRROR
            drawable.tileModeX = Shader.TileMode.MIRROR
            _background = WeakReference(drawable)
            background = drawable
        }
        return background
    }

    //分割线颜色
    @ColorInt
    abstract fun getSpacerColor(context: Context): Int

    @ColorInt
    abstract fun getLabelColor(context: Context): Int

    @ColorInt
    abstract fun getChapterTitleColor(context: Context): Int

    @ColorInt
    abstract fun getChapterContentColor(context: Context): Int

    companion object {
        fun getStyle(ordinal: Int): PageStyle {
            return values().getOrNull(ordinal) ?: STYLE_0
        }
    }
}