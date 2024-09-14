package sjj.novel.view.reader.page

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import com.sjianjun.reader.utils.hex
import sjj.alog.Log
import java.lang.ref.WeakReference

class CustomPageStyleInfo {
    var ordinal: Int = -1//自定义样式的序号从0开始
    var labelColor: Int = Color.BLACK
    var chapterTitleColor: Int = Color.BLACK
    var chapterContentColor: Int = Color.BLACK
    var backgroundColor: Int = Color.WHITE
    var backgroundImage: String = ""
    override fun toString(): String {
        return "CustomPageStyleInfo(ordinal=$ordinal, labelColor=${labelColor.hex}, chapterTitleColor=${chapterTitleColor.hex}, chapterContentColor=${chapterContentColor.hex}, backgroundColor=${backgroundColor.hex}, backgroundImage='$backgroundImage')"
    }

}

class CustomPageStyle(val info: CustomPageStyleInfo) : PageStyle(info.ordinal + defStyles.size) {
    fun clearCache() {
        cache.snapshot().keys.forEach {
            if (it.startsWith("$ordinal,")) {
                cache.remove(it)
            }
        }
    }

    override fun getBackground(context: Context, width: Int, height: Int): Drawable {
        if (info.backgroundImage.isNotEmpty()) {
            val key = key(width, height)
            var bitmap = cache.get(key)?.get()
            if (bitmap == null) {
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeFile(info.backgroundImage, options)
                options.inJustDecodeBounds = false
                options.inSampleSize = calculateInSampleSize(options, width, height)
                Log.e("inSampleSize:${options.inSampleSize}")
                bitmap = BitmapFactory.decodeFile(info.backgroundImage, options)
                cache.put(key, WeakReference(bitmap))
            }
            val drawable = BitmapDrawable(context.resources, bitmap)
            drawable.tileModeY = Shader.TileMode.MIRROR
            drawable.tileModeX = Shader.TileMode.MIRROR
            return drawable
        } else {
            return ColorDrawable(info.backgroundColor)
        }
    }

    override fun getLabelColor(context: Context): Int {
        return info.labelColor
    }

    override fun getChapterTitleColor(context: Context): Int {
        return info.chapterTitleColor
    }

    override fun getChapterContentColor(context: Context): Int {
        return info.chapterContentColor
    }
}