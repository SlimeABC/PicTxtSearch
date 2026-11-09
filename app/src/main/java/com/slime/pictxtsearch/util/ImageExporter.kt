package com.slime.pictxtsearch.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.slime.pictxtsearch.model.MatchBox
import com.slime.pictxtsearch.model.isAxisAlignedPolygon
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

object ImageExporter {

    
    fun export(
        context: Context,
        bitmap: Bitmap,
        matches: List<MatchBox>,
        highlightColor: Color,
        highlightAlpha: Float,
    ): Uri {
        val composed = renderHighlights(bitmap, matches, highlightColor, highlightAlpha)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToMediaStore(context, composed)
            } else {
                saveToLegacyGallery(context, composed)
            }
        } finally {
            if (composed !== bitmap) composed.recycle()
        }
    }

    private fun renderHighlights(
        bitmap: Bitmap,
        matches: List<MatchBox>,
        highlightColor: Color,
        highlightAlpha: Float,
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            ?: throw IOException("无法创建导出图片")
        val canvas = Canvas(result)
        val argb = highlightColor.toArgb()
        val alpha = (highlightAlpha * 255f).roundToInt().coerceIn(0, 255)
        val shortestSide = min(result.width, result.height).toFloat()
        val corner = maxOf(3f, shortestSide * 0.006f)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = argb
            this.alpha = alpha
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = argb
            this.alpha = (alpha + 70).coerceAtMost(255)
            style = Paint.Style.STROKE
            strokeWidth = maxOf(2f, shortestSide * 0.0025f)
            strokeJoin = Paint.Join.ROUND
        }

        matches.forEach { match ->
            if (match.polygon.size >= 3 && !match.polygon.isAxisAlignedPolygon()) {
                val path = Path().apply {
                    moveTo(match.polygon[0].x, match.polygon[0].y)
                    for (index in 1 until match.polygon.size) {
                        lineTo(match.polygon[index].x, match.polygon[index].y)
                    }
                    close()
                }
                canvas.drawPath(path, fillPaint)
                canvas.drawPath(path, borderPaint)
                return@forEach
            }

            val rect = RectF(
                match.box.left.toFloat(),
                match.box.top.toFloat(),
                match.box.right.toFloat(),
                match.box.bottom.toFloat(),
            )
            canvas.drawRoundRect(rect, corner, corner, fillPaint)
            canvas.drawRoundRect(rect, corner, corner, borderPaint)
        }
        return result
    }

    private fun saveToMediaStore(context: Context, bitmap: Bitmap): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName())
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/PicTxtSearch",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("无法创建相册条目")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                    throw IOException("图片编码失败")
                }
            } ?: throw IOException("无法写入相册")

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
        return uri
    }

    @Suppress("DEPRECATION")
    private fun saveToLegacyGallery(context: Context, bitmap: Bitmap): Uri {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "PicTxtSearch",
        )
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("无法创建相册目录")
        }
        val file = File(directory, fileName())
        FileOutputStream(file).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                throw IOException("图片编码失败")
            }
        }
        android.media.MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf("image/jpeg"),
            null,
        )
        return Uri.fromFile(file)
    }

    private fun fileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "PicTxtSearch_$stamp.jpg"
    }
}
