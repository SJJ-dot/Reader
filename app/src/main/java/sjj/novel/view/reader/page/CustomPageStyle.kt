package sjj.novel.view.reader.page

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import java.lang.ref.WeakReference

class CustomPageStyleInfo {
    var ordinal: Int = 0//自定义样式的序号从0开始
    var labelColor: Int = 0
    var chapterTitleColor: Int = 0
    var chapterContentColor: Int = 0
    var backgroundColor: Int = 0
    var backgroundImage: String = ""
}

class CustomPageStyle(val info: CustomPageStyleInfo) : PageStyle(info.ordinal + PageStyle.styles.size) {

    override fun getBackground(context: Context, width: Int, height: Int): Drawable {
        if (info.backgroundImage.isNotEmpty()) {
            var bitmap = cache.get("$ordinal,$width,$height")?.get()
            if (bitmap == null) {
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeFile(info.backgroundImage, options)
                options.inJustDecodeBounds = false
                options.inSampleSize = calculateInSampleSize(options, width, height)
                bitmap = BitmapFactory.decodeFile(info.backgroundImage, options)
                cache.put("$ordinal,$width,$height", WeakReference(bitmap))
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