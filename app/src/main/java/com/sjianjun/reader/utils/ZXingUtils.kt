package com.sjianjun.reader.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import java.util.*

/*
 * Created by shen jian jun on 2020-08-03
 */ /**
 * 生成条形码和二维码的工具
 */
object ZXingUtils {
    /**
     * 生成二维码 要转换的地址或字符串,可以是中文
     *
     * @param url
     * @param width
     * @param height
     * @return
     */
    fun createQRImage(url: String?, width: Int, height: Int, color: Int, bgColor: Int): Bitmap? {
        try {
            // 判断URL合法性
            if (url == null || "" == url || url.length < 1) {
                return null
            }
            val hints = Hashtable<EncodeHintType, String?>()
            hints[EncodeHintType.CHARACTER_SET] = "utf-8"
            // 图像数据转换，使用了矩阵转换
            val bitMatrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, width, height, hints)
            val pixels = IntArray(width * height)
            // 下面这里按照二维码的算法，逐个生成二维码的图片，
            // 两个for循环是图片横列扫描的结果
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (bitMatrix[x, y]) {
                        pixels[y * width + x] = color
                    } else {
                        pixels[y * width + x] = bgColor
                    }
                }
            }
            // 生成二维码图片的格式，使用ARGB_8888
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            return bitmap
        } catch (e: WriterException) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * 生成条形码
     *
     * @param context
     * @param contents
     * 需要生成的内容
     * @param desiredWidth
     * 生成条形码的宽带
     * @param desiredHeight
     * 生成条形码的高度
     * @param displayCode
     * 是否在条形码下方显示内容
     * @return
     */
    fun creatBarcode(
        context: Context?, contents: String?,
        desiredWidth: Int, desiredHeight: Int, displayCode: Boolean
    ): Bitmap? {
        var ruseltBitmap: Bitmap? = null

        /**
         * 图片两端所保留的空白的宽度
         */
        val marginW = 20

        /**
         * 条形码的编码类型
         */
        val barcodeFormat = BarcodeFormat.CODE_128
        ruseltBitmap = if (displayCode) {
            val barcodeBitmap = encodeAsBitmap(contents, barcodeFormat, desiredWidth, desiredHeight)
            val codeBitmap =
                creatCodeBitmap(contents, desiredWidth + 2 * marginW, desiredHeight, context)
            mixtureBitmap(barcodeBitmap, codeBitmap, PointF(0f, desiredHeight.toFloat()))
        } else {
            encodeAsBitmap(
                contents, barcodeFormat,
                desiredWidth, desiredHeight
            )
        }
        return ruseltBitmap
    }

    /**
     * 生成条形码的Bitmap
     *
     * @param contents
     * 需要生成的内容
     * @param format
     * 编码格式
     * @param desiredWidth
     * @param desiredHeight
     * @return
     * @throws WriterException
     */
    internal fun encodeAsBitmap(
        contents: String?,
        format: BarcodeFormat?, desiredWidth: Int, desiredHeight: Int
    ): Bitmap {
        val WHITE = -0x1
        val BLACK = -0x1000000
        val writer = MultiFormatWriter()
        var result: BitMatrix? = null
        try {
            result = writer.encode(
                contents, format, desiredWidth,
                desiredHeight, null
            )
        } catch (e: WriterException) {
            e.printStackTrace()
        }
        val width = result!!.width
        val height = result.height
        val pixels = IntArray(width * height)
        // All are 0, or black, by default
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (result[x, y]) BLACK else WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(
            width, height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * 生成显示编码的Bitmap
     *
     * @param contents
     * @param width
     * @param height
     * @param context
     * @return
     */
    internal fun creatCodeBitmap(
        contents: String?, width: Int,
        height: Int, context: Context?
    ): Bitmap {
        val tv = TextView(context)
        val layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        tv.layoutParams = layoutParams
        tv.text = contents
        tv.height = height
        tv.gravity = Gravity.CENTER_HORIZONTAL
        tv.width = width
        tv.isDrawingCacheEnabled = true
        tv.setTextColor(Color.BLACK)
        tv.measure(
            View.MeasureSpec.makeMeasureSpec(
                0,
                View.MeasureSpec.UNSPECIFIED
            ),
            View.MeasureSpec.makeMeasureSpec(
                0,
                View.MeasureSpec.UNSPECIFIED
            )
        )
        tv.layout(0, 0, tv.measuredWidth, tv.measuredHeight)
        tv.buildDrawingCache()
        return tv.drawingCache
    }

    /**
     * 将两个Bitmap合并成一个
     *
     * @param first
     * @param second
     * @param fromPoint
     * 第二个Bitmap开始绘制的起始位置（相对于第一个Bitmap）
     * @return
     */
    internal fun mixtureBitmap(
        first: Bitmap?, second: Bitmap?,
        fromPoint: PointF?
    ): Bitmap? {
        if (first == null || second == null || fromPoint == null) {
            return null
        }
        val marginW = 20
        val newBitmap = Bitmap.createBitmap(
            first.width + second.width + marginW,
            first.height + second.height, Bitmap.Config.ARGB_8888
        )
        val cv = Canvas(newBitmap)
        cv.drawBitmap(first, marginW.toFloat(), 0f, null)
        cv.drawBitmap(second, fromPoint.x, fromPoint.y, null)
        cv.save()
        cv.restore()
        return newBitmap
    }
}