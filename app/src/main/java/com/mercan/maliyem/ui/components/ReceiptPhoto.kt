package com.mercan.maliyem.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mercan.maliyem.R
import com.mercan.maliyem.data.receipt.RECEIPTS_DIRECTORY
import com.mercan.maliyem.data.receipt.RECEIPT_DRAFTS_DIRECTORY
import com.mercan.maliyem.data.receipt.existingReceipt
import com.mercan.maliyem.ui.theme.SeyirIcon
import com.mercan.maliyem.ui.theme.Spacing
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface PhotoState {
    data object Loading : PhotoState
    data object Missing : PhotoState
    data class Loaded(val image: ImageBitmap) : PhotoState
}

@Composable
private fun photoState(path: String, draft: Boolean, sample: Int): PhotoState {
    val context = LocalContext.current
    val result by produceState<PhotoState>(PhotoState.Loading, path, draft, sample, context) {
        value = PhotoState.Loading
        value = withContext(Dispatchers.IO) {
            val pending = if (draft) File(context.filesDir, RECEIPT_DRAFTS_DIRECTORY).existingReceipt(path) else null
            val file = pending ?: File(context.filesDir, RECEIPTS_DIRECTORY).existingReceipt(path)
            val bitmap = file?.let {
                BitmapFactory.decodeFile(it.path, BitmapFactory.Options().apply { inSampleSize = sample })
            }
            bitmap?.let { PhotoState.Loaded(it.asImageBitmap()) } ?: PhotoState.Missing
        }
    }
    return result
}

@Composable
fun ReceiptThumbnail(path: String, modifier: Modifier = Modifier) {
    val state = photoState(path, draft = true, sample = 4)
    Box(modifier, contentAlignment = Alignment.Center) {
        val image = (state as? PhotoState.Loaded)?.image
        if (image == null) SeyirIcon(R.drawable.ic_seyir_receipt)
        else Image(image, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun ReceiptViewer(path: String, onBack: () -> Unit, draft: Boolean = false) {
    Dialog(onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                BackHeader(stringResource(R.string.receipt_photo), onBack)
                when (val state = photoState(path, draft, sample = 1)) {
                    PhotoState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    PhotoState.Missing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        SubText(stringResource(R.string.receipt_missing))
                    }
                    is PhotoState.Loaded -> ZoomableReceipt(state.image)
                }
            }
        }
    }
}

@Composable
private fun ZoomableReceipt(image: ImageBitmap) {
    var zoom by remember(image) { mutableFloatStateOf(1f) }
    var offset by remember(image) { mutableStateOf(Offset.Zero) }
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.Center) {
            val fit = minOf(constraints.maxWidth.toFloat() / image.width, constraints.maxHeight.toFloat() / image.height)
            fun updateZoom(next: Float, candidate: Offset) {
                zoom = next.coerceIn(1f, 5f)
                val x = ((image.width * fit * zoom - constraints.maxWidth) / 2f).coerceAtLeast(0f)
                val y = ((image.height * fit * zoom - constraints.maxHeight) / 2f).coerceAtLeast(0f)
                offset = Offset(candidate.x.coerceIn(-x, x), candidate.y.coerceIn(-y, y))
            }
            Image(
                image, stringResource(R.string.receipt_photo),
                Modifier.fillMaxSize().pointerInput(image, constraints) {
                    detectTransformGestures { centroid, pan, factor, _ ->
                        val next = (zoom * factor).coerceIn(1f, 5f)
                        val relative = centroid - Offset(size.width / 2f, size.height / 2f)
                        updateZoom(next, relative + (offset - relative) * (next / zoom) + pan)
                    }
                }.graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = offset.x
                    translationY = offset.y
                },
                contentScale = ContentScale.Fit,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = {
                zoom = (zoom / 1.5f).coerceAtLeast(1f)
                offset = Offset.Zero
            }, enabled = zoom > 1f) {
                SeyirIcon(R.drawable.ic_seyir_minus, description = stringResource(R.string.receipt_zoom_out))
            }
            IconButton(onClick = {
                zoom = (zoom * 1.5f).coerceAtMost(5f)
                offset = Offset.Zero
            }, enabled = zoom < 5f) {
                SeyirIcon(R.drawable.ic_seyir_plus, description = stringResource(R.string.receipt_zoom_in))
            }
        }
    }
}
