package br.org.abrace.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun compressPhoto(context: Context, uri: Uri): ByteArray =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "Não foi possível abrir esta imagem."
        }
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = 1
                while (
                    max(bounds.outWidth, bounds.outHeight) / (inSampleSize * 2) >= 1600
                ) inSampleSize *= 2
            }
        var bitmap =
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: error("Não foi possível abrir esta imagem.")
        val orientation =
            resolver.openInputStream(uri)?.use {
                ExifInterface(it)
                    .getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
            }
        val matrix =
            Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> {
                        postRotate(90f)
                        postScale(-1f, 1f)
                    }
                    ExifInterface.ORIENTATION_TRANSVERSE -> {
                        postRotate(270f)
                        postScale(-1f, 1f)
                    }
                }
            }
        if (!matrix.isIdentity)
            bitmap =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                    if (it !== bitmap) bitmap.recycle()
                }
        val size = max(bitmap.width, bitmap.height)
        if (size > 1600)
            bitmap =
                Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * 1600f / size).roundToInt().coerceAtLeast(1),
                        (bitmap.height * 1600f / size).roundToInt().coerceAtLeast(1),
                        true,
                    )
                    .also { if (it !== bitmap) bitmap.recycle() }
        var quality = 82
        var output: ByteArray
        do {
            output =
                ByteArrayOutputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                    stream.toByteArray()
                }
            quality -= 10
        } while (output.size > 1_048_576 && quality >= 32)
        bitmap.recycle()
        require(output.size <= 1_048_576) {
            "Esta foto ainda ficou grande demais. Escolha outra imagem."
        }
        output
    }
