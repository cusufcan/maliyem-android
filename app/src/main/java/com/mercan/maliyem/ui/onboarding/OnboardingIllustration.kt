package com.mercan.maliyem.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.mercan.maliyem.R
import com.mercan.maliyem.ui.theme.SeyirIcon
import com.mercan.maliyem.util.formatMoney
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private const val ArtSize = 280f
private const val ArtCenter = ArtSize / 2f

@Composable
internal fun IntroIllustration(
    page: IntroPage,
    diameter: Dp,
    progress: () -> Float,
    phase: () -> Float,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val unit = diameter / ArtSize
    Box(modifier.size(diameter).clearAndSetSemantics {}) {
        Canvas(Modifier.fillMaxSize()) {
            val factor = size.minDimension / ArtSize
            scale(factor, factor, pivot = Offset.Zero) {
                drawCircle(colors.surfaceContainer, 107f, Offset(140f, 140f))
                drawCircle(colors.outlineVariant, 121f, Offset(140f, 140f), style = Stroke(1f))
                when (page) {
                    IntroPage.Welcome -> drawWelcome(colors, progress(), phase())
                    IntroPage.QuickAdd -> drawQuickAdd(colors, progress())
                    IntroPage.Breakdown -> drawBreakdown(colors, progress())
                    IntroPage.Goals -> drawGoals(colors, progress())
                }
            }
        }

        when (page) {
            IntroPage.Welcome -> {
                ArtIcon(R.drawable.ic_seyir_wallet, colors.onPrimary, unit, 140f, 140f, 56f)
                ArtIcon(R.drawable.ic_seyir_lock, colors.onSecondaryContainer, unit, 199f, 193f, 20f)
            }
            IntroPage.QuickAdd -> {
                val density = LocalDensity.current
                val locale = LocalConfiguration.current.locales[0]
                val p = progress()
                val cardShift = recordShift(p)
                val amount = (24_500 * ((p - .18f) / .65f).coerceIn(0f, 1f)).roundToInt().toLong()
                ArtIcon(
                    R.drawable.ic_seyir_plus, colors.onPrimaryContainer, unit, 66f, 115f, 18f,
                    Modifier.graphicsLayer { translationX = unit.toPx() * recordShift(progress()) },
                )
                Text(
                    formatMoney(amount, locale),
                    Modifier.align(Alignment.CenterStart).offset(x = unit * (90f + cardShift), y = unit * -25f),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = with(density) { (unit * 21f).toSp() },
                        lineHeight = with(density) { (unit * 27f).toSp() },
                        fontFeatureSettings = "tnum",
                    ),
                    color = colors.onSurface,
                    maxLines = 1,
                )
                ArtIcon(R.drawable.ic_seyir_receipt, colors.onSecondaryContainer, unit, 211f, 170f, 43f)
                Canvas(Modifier.fillMaxSize()) {
                    val factor = size.minDimension / ArtSize
                    scale(factor, factor, pivot = Offset.Zero) {
                        val scan = ((progress() - .25f) / .65f).coerceIn(0f, 1f)
                        val y = 153f + 34f * scan
                        drawLine(colors.secondary, Offset(188f, y), Offset(234f, y), 2.5f, StrokeCap.Round)
                    }
                }
            }
            IntroPage.Breakdown ->
                ArtIcon(R.drawable.ic_seyir_cat_chart, colors.onSurfaceVariant, unit, 106f, 117f, 34f)
            IntroPage.Goals -> {
                ArtIcon(R.drawable.ic_seyir_cat_piggy, colors.onPrimaryContainer, unit, 140f, 132f, 45f)
                ArtIcon(R.drawable.ic_seyir_repeat, colors.onSecondaryContainer, unit, 64f, 196f, 20f)
                ArtIcon(R.drawable.ic_seyir_nav_summary, colors.onTertiaryContainer, unit, 220f, 188f, 20f)
            }
        }
    }
}

@Composable
private fun BoxScope.ArtIcon(
    @DrawableRes icon: Int,
    tint: Color,
    unit: Dp,
    x: Float,
    y: Float,
    diameter: Float,
    modifier: Modifier = Modifier,
) {
    SeyirIcon(
        icon,
        Modifier.align(Alignment.Center).offset(unit * (x - ArtCenter), unit * (y - ArtCenter)).then(modifier),
        size = unit * diameter,
        tint = tint,
    )
}

private fun DrawScope.drawWelcome(colors: ColorScheme, progress: Float, phase: Float) {
    drawCircle(colors.primaryContainer, 76f, Offset(140f, 140f))
    drawCircle(colors.primary, 59f, Offset(140f, 140f))
    drawCircle(colors.secondaryContainer, 22f, Offset(199f, 193f))
    val wave = sin(phase * 2f * PI).toFloat()
    coin(Offset(65f, 83f + 7f * wave - 10f * (1f - progress)), 21f, colors.tertiaryContainer, colors.onTertiaryContainer)
    coin(Offset(220f, 83f - 6f * wave), 15f, colors.secondaryContainer, colors.onSecondaryContainer)
    coin(Offset(55f, 184f - 5f * wave), 13f, colors.primaryContainer, colors.onPrimaryContainer)
    sparkle(Offset(178f, 48f), 8f, colors.primary)
    sparkle(Offset(95f, 221f), 6f, colors.tertiary)
}

private fun DrawScope.drawQuickAdd(colors: ColorScheme, progress: Float) {
    translate(left = recordShift(progress)) {
        tile(colors.primaryContainer, 36f, 94f, 174f, 100f, 24f)
        tile(colors.surface, 36f, 86f, 174f, 100f, 24f)
        tile(colors.outlineVariant, 36f, 86f, 174f, 100f, 24f, Stroke(1f))
        drawCircle(colors.primaryContainer, 16f, Offset(66f, 115f))
        tile(colors.outlineVariant, 90f, 147f, 66f, 5f, 2.5f)
        tile(colors.surfaceVariant, 90f, 158f, 43f, 5f, 2.5f)
    }
    tile(colors.secondaryContainer, 174f, 131f, 74f, 91f, 20f)
    tile(colors.onSecondaryContainer.copy(alpha = .25f), 180f, 137f, 62f, 79f, 16f, Stroke(1f))
}

private fun recordShift(progress: Float) = (progress - 1f) * 24f

private fun DrawScope.drawBreakdown(colors: ColorScheme, progress: Float) {
    tile(colors.primaryContainer, 35f, 53f, 210f, 178f, 28f)
    tile(colors.surface, 35f, 45f, 210f, 178f, 28f)
    tile(colors.outlineVariant, 35f, 45f, 210f, 178f, 28f, Stroke(1f))
    val ring = Stroke(11f, cap = StrokeCap.Round)
    val chartCenter = Offset(106f, 117f)
    drawCircle(colors.surfaceVariant, 40f, chartCenter, style = ring)
    segment(colors.primary, -90f, 137f, progress * 360f, chartCenter, 40f, ring)
    segment(colors.secondary, 65f, 103f, progress * 360f - 155f, chartCenter, 40f, ring)
    segment(colors.tertiary, 186f, 64f, progress * 360f - 276f, chartCenter, 40f, ring)

    listOf(colors.primary, colors.secondary, colors.tertiary).forEachIndexed { index, color ->
        val y = 97f + 20f * index
        drawCircle(color, 4f, Offset(171f, y))
        tile(colors.outlineVariant, 182f, y - 2f, 36f - index * 6f, 4f, 2f)
    }
    tile(colors.surfaceVariant, 60f, 186f, 160f, 10f, 5f)
    val budget = ((progress - .25f) / .75f).coerceIn(0f, 1f)
    tile(colors.primary, 60f, 186f, 116f * budget, 10f, 5f)
}

private fun DrawScope.drawGoals(colors: ColorScheme, progress: Float) {
    val center = Offset(140f, 132f)
    val ring = Stroke(14f, cap = StrokeCap.Round)
    drawCircle(colors.primaryContainer, 48f, center)
    drawCircle(colors.surfaceVariant, 68f, center, style = ring)
    segment(colors.primary, -90f, 282f, 282f * progress, center, 68f, ring)
    tile(colors.secondaryContainer, 42f, 174f, 44f, 44f, 14f)
    tile(colors.tertiaryContainer, 198f, 166f, 44f, 44f, 14f)
    val reveal = ((progress - .70f) / .30f).coerceIn(0f, 1f)
    val glint = reveal + .55f * sin(reveal * PI).toFloat()
    sparkle(Offset(218f, 64f), 15f * glint, colors.tertiary)
    sparkle(Offset(239f, 86f), 6f * glint, colors.primary)
    sparkle(Offset(65f, 68f), 8f * glint, colors.secondary)
}

private fun DrawScope.segment(
    color: Color,
    start: Float,
    sweep: Float,
    visibleSweep: Float,
    center: Offset,
    radius: Float,
    ring: Stroke,
) {
    if (visibleSweep <= 0f) return
    drawArc(
        color, start, visibleSweep.coerceAtMost(sweep), useCenter = false,
        topLeft = center - Offset(radius, radius), size = Size(radius * 2, radius * 2), style = ring,
    )
}

private fun DrawScope.tile(
    color: Color,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    radius: Float,
    stroke: Stroke? = null,
) {
    drawRoundRect(
        color, Offset(x, y), Size(width, height), CornerRadius(radius),
        style = stroke ?: Fill,
    )
}

private fun DrawScope.coin(center: Offset, radius: Float, fill: Color, ink: Color) {
    drawCircle(fill, radius, center)
    drawCircle(ink.copy(alpha = .40f), radius * .73f, center, style = Stroke(1.4f))
    drawLine(ink, center - Offset(radius * .26f, 0f), center + Offset(radius * .26f, 0f), 1.6f, StrokeCap.Round)
}

private fun DrawScope.sparkle(center: Offset, radius: Float, color: Color) {
    val (x, y) = center
    val inset = radius * .28f
    val path = Path().apply {
        moveTo(x, y - radius)
        quadraticTo(x + inset, y - inset, x + radius, y)
        quadraticTo(x + inset, y + inset, x, y + radius)
        quadraticTo(x - inset, y + inset, x - radius, y)
        quadraticTo(x - inset, y - inset, x, y - radius)
        close()
    }
    drawPath(path, color)
}
