package com.mercan.maliyem.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.mercan.maliyem.ui.theme.LocalBackdropAlpha
import java.util.Random

@Composable
internal fun SeyirBackdrop(seed: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val alpha = LocalBackdropAlpha.current
    Box(modifier.clipToBounds().drawWithCache {
        val random = Random(seed.toLong())
        val width = size.width
        val height = size.height
        val unit = size.minDimension
        val flip = random.nextBoolean()
        fun edge(t: Float) = width * if (flip) 1f - t else t

        val circle = Offset(edge(-0.05f + random.nextFloat() * 0.10f), height * (0.04f + random.nextFloat() * 0.14f))
        val ring = Offset(edge(0.91f + random.nextFloat() * 0.12f), height * (0.04f + random.nextFloat() * 0.15f))
        val radius = unit * (0.17f + random.nextFloat() * 0.11f)
        val blobY = height * (0.24f + random.nextFloat() * 0.12f)
        val blob = Path().apply {
            moveTo(edge(1.06f), blobY - radius * 0.68f)
            cubicTo(edge(0.94f), blobY - radius, edge(0.77f), blobY - radius * 0.14f, edge(0.83f), blobY + radius * 0.42f)
            cubicTo(edge(0.87f), blobY + radius * 0.96f, edge(1.05f), blobY + radius * 0.85f, edge(1.06f), blobY - radius * 0.68f)
            close()
        }
        val dots = List(4) { index ->
            Offset(edge(0.07f + index % 2 * 0.027f), height * 0.41f + index / 2 * 10.dp.toPx())
        }
        val ringStroke = Stroke(2.dp.toPx())
        val dotRadius = 2.dp.toPx()
        onDrawBehind {
            drawCircle(colors.primary.copy(alpha = alpha), radius, circle)
            drawCircle(colors.secondary.copy(alpha = alpha), radius * 0.65f, ring, style = ringStroke)
            drawPath(blob, colors.tertiary.copy(alpha = alpha))
            dots.forEach { drawCircle(colors.primary.copy(alpha = alpha), dotRadius, it) }
        }
    })
}
