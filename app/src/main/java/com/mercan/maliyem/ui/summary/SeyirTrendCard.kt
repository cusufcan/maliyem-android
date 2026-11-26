package com.mercan.maliyem.ui.summary

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mercan.maliyem.R
import com.mercan.maliyem.ui.components.CardLabel
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SubText
import com.mercan.maliyem.ui.components.monthTitle
import com.mercan.maliyem.ui.theme.LocalMoneyColors
import com.mercan.maliyem.ui.theme.Motion
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.ui.trend.MonthlyTrend
import com.mercan.maliyem.ui.trend.percentChange
import com.mercan.maliyem.util.formatMoney
import java.math.BigInteger
import java.time.YearMonth
import java.util.Locale

/** Ay çiftleri aynı ölçeği kullanır. Son çift ortak seçili ayı gösterir. */
@Composable
internal fun SeyirTrendCard(months: List<MonthlyTrend>, locale: Locale, onSelectMonth: (YearMonth) -> Unit) {
    val current = months.lastOrNull() ?: return
    val previous = months.getOrNull(months.lastIndex - 1)
    val colors = MaterialTheme.colorScheme
    val incomeColor = LocalMoneyColors.current.onIncomeContainer
    val expenseColor = colors.onSurfaceVariant
    val maximum = months.maxOf { maxOf(it.incomeMinor, it.expenseMinor) }.coerceAtLeast(BigInteger.ONE).toDouble()

    // Çok küçük tutarlar, boş aydaki çizgiden ayırt edilecek kadar görünür kalır.
    val chartHeight = 88.dp
    val minimumFraction = 3.dp / chartHeight

    var grown by remember(current.month) { mutableStateOf(false) }
    LaunchedEffect(current.month) { grown = true }

    SeyirCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CardLabel(stringResource(R.string.trend_title))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                TrendLegend(stringResource(R.string.summary_income), incomeColor, outlined = false)
                TrendLegend(stringResource(R.string.summary_expense), expenseColor, outlined = true)
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val monthWidth = (maxWidth / months.size).coerceAtLeast(48.dp)
                val scroll = rememberScrollState()
                LaunchedEffect(scroll.maxValue) {
                    if (scroll.maxValue in 1 until Int.MAX_VALUE) scroll.scrollTo(scroll.maxValue)
                }
                Row(Modifier.horizontalScroll(scroll).selectableGroup()) {
                    months.forEach { item ->
                        val selected = item.month == current.month
                        val description = stringResource(
                            R.string.trend_month_values,
                            monthTitle("LLLL yyyy", item.month, locale),
                            formatMoney(item.incomeMinor, locale),
                            formatMoney(item.expenseMinor, locale),
                            formatMoney(item.netMinor, locale, signed = true),
                        )
                        val incomeFraction = if (item.incomeMinor.signum() == 0) 0f
                            else (item.incomeMinor.toDouble() / maximum).toFloat().coerceAtLeast(minimumFraction)
                        val expenseFraction = if (item.expenseMinor.signum() == 0) 0f
                            else (item.expenseMinor.toDouble() / maximum).toFloat().coerceAtLeast(minimumFraction)
                        val income by animateFloatAsState(
                            if (grown) incomeFraction else 0f, Motion.medium(), label = "trendIncome",
                        )
                        val expense by animateFloatAsState(
                            if (grown) expenseFraction else 0f, Motion.medium(), label = "trendExpense",
                        )
                        Column(
                            Modifier.width(monthWidth).clip(MaterialTheme.shapes.small)
                                .background(if (selected) colors.primaryContainer else Color.Transparent)
                                .selectable(selected, role = Role.Tab) { onSelectMonth(item.month) }
                                .semantics(mergeDescendants = true) { contentDescription = description }
                                .padding(vertical = Spacing.xs),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Canvas(Modifier.fillMaxWidth().height(chartHeight)) {
                                val width = minOf(size.width * .30f, 16.dp.toPx())
                                val gap = Spacing.xs.toPx()
                                val x = (size.width - 2 * width - gap) / 2f
                                val incomeHeight = if (incomeFraction == 0f) 2.dp.toPx() else size.height * income
                                val expenseHeight = if (expenseFraction == 0f) 2.dp.toPx() else size.height * expense
                                drawTrendBar(
                                    if (incomeFraction == 0f) colors.outline else incomeColor,
                                    x, width, incomeHeight, outlined = false,
                                )
                                drawTrendBar(
                                    if (expenseFraction == 0f) colors.outline else expenseColor,
                                    x + width + gap, width, expenseHeight, outlined = true,
                                )
                            }
                            BasicText(
                                monthTitle("LLL", item.month, locale),
                                modifier = Modifier.clearAndSetSemantics { },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                ),
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp, stepSize = 1.sp),
                            )
                        }
                    }
                }
            }
            val net = formatMoney(current.netMinor, locale, signed = true)
            if (previous == null) {
                SubText(stringResource(R.string.trend_net, net))
            } else {
                SubText(stringResource(R.string.trend_net_comparison, net, formatMoney(previous.netMinor, locale, signed = true)))
            }
            val change = previous?.let { percentChange(current.expenseMinor, it.expenseMinor) }
            SubText(
                when (change?.signum()) {
                    null -> stringResource(R.string.trend_expense_unavailable, stringResource(R.string.trend_unavailable))
                    0 -> stringResource(R.string.trend_expense_steady)
                    1 -> stringResource(R.string.trend_expense_up, change.toString())
                    else -> stringResource(R.string.trend_expense_down, change.abs().toString())
                },
            )
        }
    }
}

@Composable
private fun TrendLegend(label: String, color: Color, outlined: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawTrendBar(color, 0f, size.width, size.height, outlined) }
        SubText(label)
    }
}

/** Çerçeve Canvas sınırlarının içinde kalır ve kısa çubukların yüksekliğini değiştirmez. */
private fun DrawScope.drawTrendBar(color: Color, x: Float, width: Float, height: Float, outlined: Boolean) {
    if (height <= 0f || width <= 0f) return
    val y = size.height - height
    val corner = CornerRadius(minOf(3.dp.toPx(), height / 2f))
    if (outlined) {
        val stroke = minOf(2.dp.toPx(), width / 3f, height / 3f)
        val inset = stroke / 2f
        drawRoundRect(color, Offset(x + inset, y + inset), Size(width - stroke, height - stroke), corner, style = Stroke(stroke))
    } else {
        drawRoundRect(color, Offset(x, y), Size(width, height), corner)
    }
}
