package com.mercan.maliyem.ui.breakdown

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mercan.maliyem.R
import com.mercan.maliyem.ui.components.categoryName
import com.mercan.maliyem.ui.theme.LocalReducedMotion
import com.mercan.maliyem.ui.theme.Motion
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.util.formatMoney
import java.math.BigInteger
import java.util.Locale
import kotlin.math.atan2

// The last palette color is reserved for the group of remaining categories.
internal fun List<Color>.categoryColor(categoryId: Long): Color =
    this[Math.floorMod(categoryId, (size - 1).toLong()).toInt()]

private val RingWidth = 22.dp

private fun gapDegrees(gapPx: Float, radius: Float): Float =
    Math.toDegrees((gapPx / radius).toDouble()).toFloat()

@Composable
internal fun BreakdownDonut(
    slices: List<DonutSlice>,
    total: BigInteger,
    typeLabel: String,
    locale: Locale,
    palette: List<Color>,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val reveal by animateFloatAsState(
        if (shown || reducedMotion) 360f else 0f,
        animationSpec = if (reducedMotion) snap() else Motion.medium(),
        label = "donutSweep",
    )

    var selected by remember(slices) { mutableIntStateOf(-1) }
    val names = slices.map { slice ->
        slice.category?.let { categoryName(it.key, it.name) } ?: stringResource(R.string.breakdown_other)
    }
    val labels = slices.mapIndexed { index, slice ->
        stringResource(R.string.breakdown_chart_category, names[index], stringResource(R.string.percent, slice.percent))
    }
    val selectedLabel = names.getOrNull(selected) ?: typeLabel
    val selectedAmount = formatMoney(slices.getOrNull(selected)?.amountMinor ?: total, locale)
    val selectedText = slices.getOrNull(selected)?.let {
        stringResource(R.string.breakdown_chart_category, selectedLabel, stringResource(R.string.percent, it.percent))
    } ?: typeLabel
    val description = stringResource(
        R.string.breakdown_chart_description, selectedText, selectedAmount, labels.joinToString(),
    )
    val resetLabel = stringResource(R.string.breakdown_chart_reset)

    Box(
        modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f)
            .clearAndSetSemantics {
                contentDescription = description
                customActions = labels.mapIndexed { index, label ->
                    CustomAccessibilityAction(label) {
                        selected = if (selected == index) -1 else index
                        true
                    }
                } + CustomAccessibilityAction(resetLabel) {
                    selected = -1
                    true
                }
            }
            .pointerInput(slices) {
                detectTapGestures { tap ->
                    val offset = tap - Offset(size.width / 2f, size.height / 2f)
                    val distance = offset.getDistance()
                    val outerRadius = size.width / 2f
                    val radius = outerRadius - RingWidth.toPx() / 2f
                    if (distance < outerRadius - 48.dp.toPx()) {
                        selected = -1
                    } else if (distance <= outerRadius) {
                        val angle = (Math.toDegrees(atan2(offset.y, offset.x).toDouble()) + 450.0).toFloat() % 360f
                        val gap = if (slices.size == 1) 0f else gapDegrees(2.dp.toPx(), radius)
                        val index = slices.indexOfFirst { it.visibleArc(gap).contains(angle) }
                        if (index >= 0) selected = if (selected == index) -1 else index
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = RingWidth.toPx()
            val radius = (size.width - stroke) / 2f
            if (radius <= 0f) return@Canvas
            val gap = if (slices.size == 1) 0f else gapDegrees(2.dp.toPx(), radius)
            val arcSize = Size(radius * 2, radius * 2)
            val inset = Offset(stroke / 2, stroke / 2)
            slices.forEachIndexed { index, slice ->
                val arc = slice.visibleArc(gap)
                val sweep = minOf(arc.sweepDegrees, reveal - arc.startDegrees)
                if (sweep > 0f) {
                    val color = slice.category?.let { palette.categoryColor(it.categoryId) } ?: palette.last()
                    drawArc(
                        color = color.copy(alpha = if (selected == -1 || selected == index) 1f else 0.35f),
                        startAngle = arc.startDegrees - 90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = inset,
                        size = arcSize,
                        style = Stroke(stroke),
                    )
                }
            }
        }
        Column(
            Modifier.fillMaxWidth(0.66f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                selectedLabel, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                selectedAmount, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 24.sp),
            )
            slices.getOrNull(selected)?.let {
                Text(
                    stringResource(R.string.percent, it.percent), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
