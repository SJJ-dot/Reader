package sjj.novel.view.reader.page

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.annotation.ColorInt
import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.R
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.color
import java.lang.ref.WeakReference

/*
 * Created by shen jian jun on 2020-07-13
 */
abstract class PageStyle(val id: String) {
    protected val cache get() = lruCache

    protected fun key(width: Int, height: Int) = "$id,$width,$height"

    open fun getBackgroundSync(context: Context, width: Int, height: Int): Drawable? {
        val bitmap = cache.get(key(width, height))?.get()
        if (bitmap != null) {
            val drawable = BitmapDrawable(context.resources, bitmap)
            drawable.tileModeY = Shader.TileMode.MIRROR
            drawable.tileModeX = Shader.TileMode.MIRROR
            return drawable
        }
        return null
    }


    protected fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
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
        var bitmap = cache.get(key(width, height))?.get()
        if (bitmap == null) {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeResource(context.resources, resId, options)
            options.inJustDecodeBounds = false
            options.inSampleSize = calculateInSampleSize(options, width, height)
            bitmap = BitmapFactory.decodeResource(context.resources, resId, options)
            cache.put(key(width, height), WeakReference(bitmap))
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
        val styles = MutableLiveData<List<PageStyle>>()

        @JvmStatic
        val defDay: PageStyle get() = styles.value!!.first { !it.isDark }

        @JvmStatic
        val defNight: PageStyle get() = styles.value!!.first { it.isDark }

        private fun builtin(): List<CustomPageStyleInfo> {
            val infos = mutableListOf<CustomPageStyleInfo>()
            infos.add(CustomPageStyleInfo().apply {
                labelColor = "#66000000".color
                chapterTitleColor = "#d9000000".color
                chapterContentColor = chapterTitleColor
                backgroundColor = "#ffffff".color
                isDark = false
                isDeleteable = false
                isBuiltin = true
            })
            infos.add(CustomPageStyleInfo().apply {
                labelColor = "#80ffffff".color
                chapterTitleColor = "#80ffffff".color
                chapterContentColor = chapterTitleColor
                backgroundColor = "#1a1a1a".color
                isDark = true
                isDeleteable = false
                isBuiltin = true
            })
            infos.add(CustomPageStyleInfo().apply {
                labelColor = "#666666".color
                chapterTitleColor = "#886A66".color
                chapterContentColor = "#3E3C38".color
                backgroundRes = R.drawable.ic_reader_style1_bg
                isDark = false
                isBuiltin = true
            })

            infos.add(CustomPageStyleInfo().apply {
                labelColor = "#BDBDBD".color
                chapterTitleColor = "#BDBDBD".color
                chapterContentColor = "#999999".color
                backgroundRes = R.drawable.ic_reader_style2_bg
                isDark = true
                isBuiltin = true

            })
            infos.add(CustomPageStyleInfo().apply {
                labelColor = "#883B2405".color
                chapterTitleColor = "#3B2405".color
                chapterContentColor = "#3B2405".color
                backgroundRes = R.drawable.ic_reader_style3_bg
                isDark = false
                isBuiltin = true
            })
            infos.add(CustomPageStyleInfo().apply {
                labelColor = "#88422D10".color
                chapterTitleColor = "#422D10".color
                chapterContentColor = "#422D10".color
                backgroundRes = R.drawable.ic_reader_style4_bg
                isDark = false
                isBuiltin = true
            })
            infos.add(CustomPageStyleInfo().apply {
                labelColor = "#883C1B12".color
                chapterTitleColor = "#3C1B12".color
                chapterContentColor = "#3C1B12".color
                backgroundRes = R.drawable.ic_reader_style5_bg
                isDark = false
                isBuiltin = true
            })
            infos.add(CustomPageStyleInfo().apply {
                labelColor = "#881D321F".color
                chapterTitleColor = "#1D321F".color
                chapterContentColor = "#1D321F".color
                backgroundRes = R.drawable.ic_reader_style6_bg
                isDark = false
                isBuiltin = true
            })
            infos.add(CustomPageStyleInfo().apply {
                labelColor = "#88A4A2A5".color
                chapterTitleColor = "#A4A2A5".color
                chapterContentColor = "#A4A2A5".color
                backgroundRes = R.drawable.ic_reader_style7_bg
                isDark = true
                isBuiltin = true
            })
            infos.add(CustomPageStyleInfo().apply {
                labelColor = "#8829251A".color
                chapterTitleColor = "#29251A".color
                chapterContentColor = "#29251A".color
                backgroundRes = R.drawable.ic_reader_style8_bg
                isDark = false
                isBuiltin = true
            })
            infos.add(CustomPageStyleInfo().apply {
                labelColor = "#88222421".color
                chapterTitleColor = "#222421".color
                chapterContentColor = "#222421".color
                backgroundRes = R.drawable.ic_reader_style9_bg
                isDark = false
                isBuiltin = true
            })
            infos.add(CustomPageStyleInfo().apply {
                labelColor = "#883B3221".color
                chapterTitleColor = "#3B3221".color
                chapterContentColor = "#3B3221".color
                backgroundRes = R.drawable.ic_reader_style10_bg
                isDark = false
                isBuiltin = true
            })
            infos.add(CustomPageStyleInfo().apply {
                labelColor = "#88202020".color
                chapterTitleColor = "#202020".color
                chapterContentColor = "#202020".color
                backgroundRes = R.drawable.ic_reader_style11_bg
                isDark = false
                isBuiltin = true
            })
            infos.add(CustomPageStyleInfo().apply {
                labelColor = "#88292019".color
                chapterTitleColor = "#292019".color
                chapterContentColor = "#292019".color
                backgroundRes = R.drawable.ic_reader_style12_bg
                isDark = false
                isBuiltin = true
            })
            return infos
        }

        fun restoreBuiltinStyles() {
            val newStyles = mutableListOf<CustomPageStyleInfo>()
            newStyles.addAll(builtin())
            val infos = globalConfig.customPageStyleInfoList.value
            newStyles.addAll(infos!!.filter { !it.isBuiltin })
            globalConfig.customPageStyleInfoList.setValue(newStyles)
            val info = newStyles.firstOrNull { it.id == globalConfig.readerPageStyle.value }
            if (info == null) {
                globalConfig.readerPageStyle.setValue(defDay.id)
            }
        }

        init {
            globalConfig.customPageStyleInfoList.observeForever {
                val newStyles = mutableListOf<PageStyle>()
                it.forEach { info ->
                    newStyles.add(CustomPageStyle(info))
                }
                styles.value = newStyles
            }
            if (globalConfig.customPageStyleInfoList.value.isNullOrEmpty()) {
                restoreBuiltinStyles()
            }
        }

        @JvmStatic
        fun getStyle(id: String): PageStyle {
            return styles.value!!.firstOrNull { it.id == id } ?: defDay
        }
    }
}












