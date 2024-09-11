package sjj.novel.view.reader.page

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable

class BgDrawable(private val drawable: Drawable) : Drawable() {
    private val rect: Rect = Rect()
    override fun draw(canvas: Canvas) {
        canvas.getClipBounds(rect)
        drawable.bounds = rect
        drawable.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        drawable.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        drawable.colorFilter = colorFilter
    }

    override fun getOpacity(): Int {
        return drawable.opacity
    }
}