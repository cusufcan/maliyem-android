package com.mercan.maliyem.data.receipt

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.time.Clock
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class ReceiptTextReader @Inject constructor(clock: Clock) {
    private val parser = ReceiptParser(clock)

    suspend fun read(file: File): ReceiptSuggestion? = withContext(Dispatchers.IO) {
        val bitmap = BitmapFactory.decodeFile(file.path) ?: return@withContext null
        val text = suspendCancellableCoroutine<Text?> { continuation ->
            if (!continuation.isActive) {
                bitmap.recycle()
                return@suspendCancellableCoroutine
            }
            try {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    recognizer.process(InputImage.fromBitmap(bitmap, 0))
                        .addOnCompleteListener(Executor(Runnable::run)) { task ->
                            // ML Kit's task has no cancellation token. Keep its bitmap alive until
                            // it completes, even if the form has already abandoned the result.
                            recognizer.close()
                            bitmap.recycle()
                            val text = if (task.isSuccessful) task.result else null
                            if (continuation.isActive) continuation.resume(text)
                        }
                } catch (failure: RuntimeException) {
                    recognizer.close()
                    throw failure
                }
            } catch (_: RuntimeException) {
                bitmap.recycle()
                continuation.resume(null)
            }
        } ?: return@withContext null
        parser.parse(text.receiptLines())
    }
}

// ML Kit can put the left-hand label and right-hand price in separate blocks. A tilted photo
// shifts the right-hand price up or down, so rows are grouped on deskewed coordinates.
private fun Text.receiptLines(): List<ReceiptLine> {
    val lines = textBlocks.flatMap { it.lines }
    if (lines.any { it.boundingBox == null }) return lines.map { ReceiptLine(it.text) }

    val angles = lines.map { it.angle.toDouble() }.sorted()
    val skew = Math.toRadians(angles[angles.size / 2])
    val sin = sin(skew)
    val cos = cos(skew)
    data class Positioned(val text: String, val left: Double, val center: Double, val height: Int)
    val positioned = lines.map { line ->
        val box = line.boundingBox!!
        val cx = box.exactCenterX().toDouble()
        val cy = box.exactCenterY().toDouble()
        // Line boxes are axis-aligned, so their height includes the tilt; use the text height.
        val height = line.cornerPoints?.takeIf { it.size == 4 }
            ?.let { p -> hypot((p[3].x - p[0].x).toDouble(), (p[3].y - p[0].y).toDouble()).toInt() }
            ?: box.height()
        Positioned(line.text, cx * cos + cy * sin, cy * cos - cx * sin, height)
    }

    val rows = mutableListOf<MutableList<Positioned>>()
    for (line in positioned.sortedBy { it.center }) {
        val row = rows.lastOrNull()
        val anchor = row?.first()
        if (anchor != null && abs(line.center - anchor.center) <= minOf(line.height, anchor.height) / 2.0) {
            row.add(line)
        } else {
            rows.add(mutableListOf(line))
        }
    }
    return rows.mapIndexed { index, row ->
        ReceiptLine(row.sortedBy { it.left }.joinToString(" ") { it.text }, index)
    }
}
