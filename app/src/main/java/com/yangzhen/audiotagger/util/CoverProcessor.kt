package com.yangzhen.audiotagger.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object CoverProcessor {

    private const val MAX_SIDE = 1200
    private const val JPEG_QUALITY = 88

    fun loadFromUri(context: Context, uri: Uri): Result<ByteArray> = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        process(source)
    }

    fun loadFromFile(file: File): Result<ByteArray> = runCatching {
        if (!file.isFile) error("封面文件不存在")
        val source = ImageDecoder.createSource(file)
        process(source)
    }

    fun loadFromBytes(bytes: ByteArray): Result<ByteArray> = runCatching {
        if (bytes.isEmpty()) error("封面图片为空")
        val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
        process(source)
    }

    private fun process(source: ImageDecoder.Source): ByteArray {
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / longest.toFloat()
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
        }

        val side = min(bitmap.width, bitmap.height)
        require(side > 0) { "封面尺寸无效" }
        val left = (bitmap.width - side) / 2
        val top = (bitmap.height - side) / 2

        val square = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(square)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(
            bitmap,
            Rect(left, top, left + side, top + side),
            Rect(0, 0, side, side),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )

        val output = ByteArrayOutputStream()
        square.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        if (bitmap !== square) bitmap.recycle()
        square.recycle()
        return output.toByteArray()
    }
}

