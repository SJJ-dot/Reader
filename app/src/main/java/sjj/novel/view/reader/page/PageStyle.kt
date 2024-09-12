package sjj.novel.view.reader.page

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.annotation.ColorInt
import com.bumptech.glide.Glide
import com.sjianjun.reader.R
import com.sjianjun.reader.utils.color
import java.lang.ref.WeakReference

/*
 * Created by shen jian jun on 2020-07-13
 */
abstract class PageStyle(val ordinal: Int) {

    open fun getBackgroundSync(context: Context, width: Int, height: Int): Drawable? {
        val bitmap = lruCache.get("$ordinal,$width,$height")?.get()
        if (bitmap != null) {
            val drawable = BitmapDrawable(context.resources, bitmap)
            drawable.tileModeY = Shader.TileMode.MIRROR
            drawable.tileModeX = Shader.TileMode.MIRROR
            return drawable
        }
        return null
    }


    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // 图片的原始宽高
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (reqHeight in 1 until height || reqWidth in 1 until width) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // 计算宽高的最大缩放比例
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    fun createBackground(context: Context, resId: Int, width: Int, height: Int): Drawable {
        var bitmap = lruCache.get("$ordinal,$width,$height")?.get()
        if (bitmap == null) {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeResource(context.resources, resId, options)
            options.inJustDecodeBounds = false
            options.inSampleSize = calculateInSampleSize(options, width, height)
            bitmap = BitmapFactory.decodeResource(context.resources, resId, options)
            lruCache.put("$ordinal,$width,$height", WeakReference(bitmap))
        }
        val drawable = BitmapDrawable(context.resources, bitmap)
        drawable.tileModeY = Shader.TileMode.MIRROR
        drawable.tileModeX = Shader.TileMode.MIRROR
        return drawable
    }

    open val isDark = false

    abstract fun getBackground(context: Context, width: Int = 0, height: Int = 0): Drawable

    @ColorInt
    abstract fun getLabelColor(context: Context): Int

    @ColorInt
    abstract fun getChapterTitleColor(context: Context): Int

    @ColorInt
    abstract fun getChapterContentColor(context: Context): Int

    open fun getSelectedColor(context: Context): Int {
        return Color.parseColor("#ffd54f")
    }

    companion object {
        private val lruCache = LruCache<String, WeakReference<Bitmap>>(8)

        @JvmField
        val styles: List<PageStyle> = listOf(Style0(), Style1(), Style2(), Style3(), Style4(), Style5(), Style6(), Style7(), Style8(), Style9(), Style10(), Style11(), Style12())

        @JvmStatic
        fun getStyle(ordinal: Int): PageStyle {
            return styles.getOrNull(ordinal) ?: styles[0]
        }
    }
}


private class Style0 : PageStyle(0) {
    override fun getBackgroundSync(context: Context, width: Int, height: Int): Drawable {
        return getBackground(context, width, height)
    }

    override fun getBackground(context: Context, width: Int, height: Int): Drawable {
        return ColorDrawable(R.color.dn_reader_content_background.color(context))
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
}

private class Style1 : PageStyle(1) {
    override fun getBackground(context: Context, width: Int, height: Int): Drawable {
        return createBackground(context, R.drawable.ic_reader_style1_bg, width, height)
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
}

private class Style2 : PageStyle(2) {
    override val isDark: Boolean = true

    override fun getBackground(context: Context, width: Int, height: Int): Drawable {
        return createBackground(context, R.drawable.ic_reader_style2_bg, width, height)
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
}

private class Style3 : PageStyle(3) {
    override fun getBackground(context: Context, width: Int, height: Int): Drawable {
        return createBackground(context, R.drawable.ic_reader_style3_bg, width, height)
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
}

private class Style4 : PageStyle(4) {
    override fun getBackground(context: Context, width: Int, height: Int): Drawable {
        return createBackground(context, R.drawable.ic_reader_style4_bg, width, height)
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
}

private class Style5 : PageStyle(5) {
    override fun getBackground(context: Context, width: Int, height: Int): Drawable {
        return createBackground(context, R.drawable.ic_reader_style5_bg, width, height)
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
}

private class Style6 : PageStyle(6) {
    override fun getBackground(context: Context, width: Int, height: Int): Drawable {
        return createBackground(context, R.drawable.ic_reader_style6_bg, width, height)
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
}

private class Style7 : PageStyle(7) {
    override val isDark: Boolean = true

    override fun getBackground(context: Context, width: Int, height: Int): Drawable {
        return createBackground(context, R.drawable.ic_reader_style7_bg, width, height)
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
}

private class Style8 : PageStyle(8) {
    override fun getBackground(context: Context, width: Int, height: Int): Drawable {
        return createBackground(context, R.drawable.ic_reader_style8_bg, width, height)
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
}

private class Style9 : PageStyle(9) {
    override fun getBackground(context: Context, width: Int, height: Int): Drawable {
        return createBackground(context, R.drawable.ic_reader_style9_bg, width, height)
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
}

private class Style10 : PageStyle(10) {
    override fun getBackground(context: Context, width: Int, height: Int): Drawable {
        return createBackground(context, R.drawable.ic_reader_style10_bg, width, height)
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
}

private class Style11 : PageStyle(11) {
    override fun getBackground(context: Context, width: Int, height: Int): Drawable {
        return createBackground(context, R.drawable.ic_reader_style11_bg, width, height)
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
}

private class Style12 : PageStyle(12) {
    override fun getBackground(context: Context, width: Int, height: Int): Drawable {
        return createBackground(context, R.drawable.ic_reader_style12_bg, width, height)
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

}