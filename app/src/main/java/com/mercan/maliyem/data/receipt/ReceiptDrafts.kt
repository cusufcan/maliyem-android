package com.mercan.maliyem.data.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReceiptDrafts @Inject constructor(@ApplicationContext context: Context) {
    private val context = context
    private val directory = File(context.filesDir, RECEIPT_DRAFTS_DIRECTORY)
    private val camera = File(context.filesDir, RECEIPT_CAMERA_DIRECTORY)
    private val saved = File(context.filesDir, RECEIPTS_DIRECTORY)

    fun newName(): String = "${UUID.randomUUID()}.jpg"

    fun cameraUri(name: String): Uri {
        val file = checkNotNull(camera.receiptFile(name))
        camera.mkdirs()
        return FileProvider.getUriForFile(context, "${context.packageName}.$RECEIPT_CAMERA_DIRECTORY", file)
    }

    fun discardCapture(name: String?) { camera.receiptFile(name)?.delete() }
    fun discardDraft(name: String?) { directory.receiptFile(name)?.delete() }

    internal fun draftFile(name: String): File? = directory.existingReceipt(name)

    suspend fun hasPhoto(name: String?): Boolean = withContext(Dispatchers.IO) {
        directory.existingReceipt(name) != null || saved.existingReceipt(name) != null
    }

    suspend fun importPhoto(uri: Uri, name: String) = withContext(Dispatchers.IO) {
        val target = checkNotNull(directory.receiptFile(name))
        val bitmap = if (Build.VERSION.SDK_INT >= 28) decodeModern(uri) else decodeLegacy(uri)
        try {
            val (width, height) = boundedSize(bitmap.width, bitmap.height)
            val resized = bitmap.scale(width, height)
            try {
                directory.mkdirs()
                target.outputStream().use {
                    if (!resized.compress(Bitmap.CompressFormat.JPEG, 80, it)) {
                        throw IOException("Could not encode receipt")
                    }
                }
            } finally {
                if (resized !== bitmap) resized.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    @RequiresApi(28)
    private fun decodeModern(uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val (width, height) = boundedSize(info.size.width, info.size.height)
            decoder.setTargetSize(width, height)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    private fun decodeLegacy(uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        val longEdge = maxOf(options.outWidth, options.outHeight)
        if (longEdge <= 0) throw IOException("Invalid receipt dimensions")
        options.inJustDecodeBounds = false
        options.inSampleSize = Integer.highestOneBit(maxOf(1, longEdge / MAX_EDGE))
        val exif = try {
            resolver.openInputStream(uri)?.use { ExifInterface(it) }
        } catch (_: IOException) {
            null
        }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Could not decode receipt")
        val transform = Matrix()
        if (exif?.isFlipped == true) transform.postScale(-1f, 1f)
        if (exif != null) transform.postRotate(exif.rotationDegrees.toFloat())
        if (transform.isIdentity) return bitmap
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true)
        } finally {
            bitmap.recycle()
        }
    }
}

private const val MAX_EDGE = 1600
internal fun boundedSize(width: Int, height: Int): Pair<Int, Int> {
    val scale = minOf(1.0, MAX_EDGE.toDouble() / maxOf(width, height))
    return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
}
