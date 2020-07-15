package com.sjianjun.reader.module.reader.style

import android.content.Context
import android.graphics.Bitmap
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
        override val canScroll: Boolean = false
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
            return createBackground(context, R.drawable.ic_reader_style1_bg)
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
    },
    STYLE_2 {
        override val isDark: Boolean = true
        override val canScroll: Boolean = false
        override fun getBackground(context: Context): Drawable {
            return createBackground(context, R.drawable.ic_reader_style2_bg)
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
    },
    STYLE_3 {

        override fun getBackground(context: Context): Drawable {
            return createBackground(context, R.drawable.ic_reader_style3_bg)
        }

        override fun getSpacerColor(context: Context): Int {
            return getLabelColor(context)
        }

        override fun getLabelColor(context: Context): Int {
            return "#883B2405".color
        }

        override fun getChapterTitleColor(context: Context): Int {
            return "#3B2405".color
        }

        override fun getChapterContentColor(context: Context): Int {
            return "#3B2405".color
        }
    },
    STYLE_4 {

        override fun getBackground(context: Context): Drawable {
            return createBackground(context, R.drawable.ic_reader_style4_bg)
        }

        override fun getSpacerColor(context: Context): Int {
            return getLabelColor(context)
        }

        override fun getLabelColor(context: Context): Int {
            return "#88422D10".color
        }

        override fun getChapterTitleColor(context: Context): Int {
            return "#422D10".color
        }

        override fun getChapterContentColor(context: Context): Int {
            return "#422D10".color
        }
    },
    STYLE_5 {
        override val canScroll: Boolean = false
        override fun getBackground(context: Context): Drawable {
            return createBackground(context, R.drawable.ic_reader_style5_bg)
        }

        override fun getSpacerColor(context: Context): Int {
            return getLabelColor(context)
        }

        override fun getLabelColor(context: Context): Int {
            return "#883C1B12".color
        }

        override fun getChapterTitleColor(context: Context): Int {
            return "#3C1B12".color
        }

        override fun getChapterContentColor(context: Context): Int {
            return "#3C1B12".color
        }
    },
    STYLE_6 {
        override val canScroll: Boolean = false
        override fun getBackground(context: Context): Drawable {
            return createBackground(context, R.drawable.ic_reader_style6_bg)
        }

        override fun getSpacerColor(context: Context): Int {
            return getLabelColor(context)
        }

        override fun getLabelColor(context: Context): Int {
            return "#881D321F".color
        }

        override fun getChapterTitleColor(context: Context): Int {
            return "#1D321F".color
        }

        override fun getChapterContentColor(context: Context): Int {
            return "#1D321F".color
        }
    },
    STYLE_7 {
        override val isDark: Boolean = true
        override val canScroll: Boolean = false
        override fun getBackground(context: Context): Drawable {
            return createBackground(context, R.drawable.ic_reader_style7_bg)
        }

        override fun getSpacerColor(context: Context): Int {
            return getLabelColor(context)
        }

        override fun getLabelColor(context: Context): Int {
            return "#88A4A2A5".color
        }

        override fun getChapterTitleColor(context: Context): Int {
            return "#A4A2A5".color
        }

        override fun getChapterContentColor(context: Context): Int {
            return "#A4A2A5".color
        }
    },
    STYLE_8 {
        override val canScroll: Boolean = false
        override fun getBackground(context: Context): Drawable {
            return createBackground(context, R.drawable.ic_reader_style8_bg)
        }

        override fun getSpacerColor(context: Context): Int {
            return getLabelColor(context)
        }

        override fun getLabelColor(context: Context): Int {
            return "#8829251A".color
        }

        override fun getChapterTitleColor(context: Context): Int {
            return "#29251A".color
        }

        override fun getChapterContentColor(context: Context): Int {
            return "#29251A".color
        }
    },
    STYLE_9 {
        override val canScroll: Boolean = false
        override fun getBackground(context: Context): Drawable {
            return createBackground(context, R.drawable.ic_reader_style9_bg)
        }

        override fun getSpacerColor(context: Context): Int {
            return getLabelColor(context)
        }

        override fun getLabelColor(context: Context): Int {
            return "#88222421".color
        }

        override fun getChapterTitleColor(context: Context): Int {
            return "#222421".color
        }

        override fun getChapterContentColor(context: Context): Int {
            return "#222421".color
        }
    },
    STYLE_10 {

        override fun getBackground(context: Context): Drawable {
            return createBackground(context, R.drawable.ic_reader_style10_bg)
        }

        override fun getSpacerColor(context: Context): Int {
            return getLabelColor(context)
        }

        override fun getLabelColor(context: Context): Int {
            return "#883B3221".color
        }

        override fun getChapterTitleColor(context: Context): Int {
            return "#3B3221".color
        }

        override fun getChapterContentColor(context: Context): Int {
            return "#3B3221".color
        }
    },
    STYLE_11 {
        override val canScroll: Boolean = false
        override fun getBackground(context: Context): Drawable {
            return createBackground(context, R.drawable.ic_reader_style11_bg)
        }

        override fun getSpacerColor(context: Context): Int {
            return getLabelColor(context)
        }

        override fun getLabelColor(context: Context): Int {
            return "#88202020".color
        }

        override fun getChapterTitleColor(context: Context): Int {
            return "#202020".color
        }

        override fun getChapterContentColor(context: Context): Int {
            return "#202020".color
        }
    },
    STYLE_12 {
        override val canScroll: Boolean = false
        override fun getBackground(context: Context): Drawable {
            return createBackground(context, R.drawable.ic_reader_style12_bg)
        }

        override fun getSpacerColor(context: Context): Int {
            return getLabelColor(context)
        }

        override fun getLabelColor(context: Context): Int {
            return "#88292019".color
        }

        override fun getChapterTitleColor(context: Context): Int {
            return "#292019".color
        }

        override fun getChapterContentColor(context: Context): Int {
            return "#292019".color
        }
    };

    abstract fun getBackground(context: Context): Drawable

    private var _bitmap: WeakReference<Bitmap>? = null
    fun createBackground(context: Context, resId: Int): Drawable {
        var bitmap = _bitmap?.get()
        if (bitmap == null) {
            bitmap = BitmapFactory.decodeResource(context.resources, resId)
            _bitmap = WeakReference(bitmap)
        }
        val drawable = BitmapDrawable(context.resources, bitmap)
        drawable.tileModeY = Shader.TileMode.REPEAT
        drawable.tileModeX = Shader.TileMode.MIRROR
        return drawable
    }

    open val isDark = false
    //背景是否跟随内容滑动
    open val canScroll = true

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