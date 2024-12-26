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
import java.util.UUID

class CustomPageStyleInfo {
    var id: String = UUID.randomUUID().toString().replace("-", "")
    var labelColor: Int = Color.BLACK
    var chapterTitleColor: Int = Color.BLACK
    var chapterContentColor: Int = Color.BLACK
    var backgroundColor: Int = Color.WHITE
    var backgroundImage: String = ""
    var backgroundResName: String = "" // 资源id 名称
    var isDark: Boolean = false
    var isDeleteable: Boolean = true
    var isBuiltin = false
    override fun toString(): String {
        return "CustomPageStyleInfo(id=$id, labelColor=${labelColor.hex}, chapterTitleColor=${chapterTitleColor.hex}, chapterContentColor=${chapterContentColor.hex}, backgroundColor=${backgroundColor.hex}, backgroundImage='$backgroundImage')"
    }

}

class CustomPageStyle(val info: CustomPageStyleInfo) : PageStyle(info.id) {
    override val isDark: Boolean
        get() = info.isDark

    fun clearCache() {
        cache.snapshot().keys.forEach {
            if (it.startsWith("$id,")) {
                cache.remove(it)
            }
        }
    }

    override fun getBackgroundSync(context: Context, width: Int, height: Int): Drawable? {
        val drawable = super.getBackgroundSync(context, width, height)
        if (drawable != null) {
            return drawable
        }
        if (info.backgroundImage.isEmpty() && info.backgroundResName.isBlank()) {
            return getBackground(context, width, height)
        }
        return null
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
        } else if (info.backgroundResName.isNotBlank()) {
            return createBackground(
                context,
                resNameMap[info.backgroundResName]!!,
                width,
                height
            )
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