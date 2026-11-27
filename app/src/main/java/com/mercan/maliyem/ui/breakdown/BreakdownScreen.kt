package com.mercan.maliyem.ui.breakdown

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.ui.budgets.budgetAmounts
import com.mercan.maliyem.ui.components.CategoryCircle
import com.mercan.maliyem.ui.components.EmptyState
import com.mercan.maliyem.ui.components.ScreenLoading
import com.mercan.maliyem.ui.components.ScreenTitle
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SeyirSegmented
import com.mercan.maliyem.ui.components.SubText
import com.mercan.maliyem.ui.components.categoryName
import com.mercan.maliyem.ui.components.monthTitle
import com.mercan.maliyem.ui.theme.LocalCategoryPalette
import com.mercan.maliyem.ui.theme.LocalReducedMotion
import com.mercan.maliyem.ui.theme.Motion
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.ui.theme.categoryIcon
import com.mercan.maliyem.ui.theme.slideFade
import com.mercan.maliyem.util.formatMoney
import java.time.YearMonth
import java.util.Locale

private val types = listOf(TransactionType.EXPENSE, TransactionType.INCOME)

@Composable
fun BreakdownScreen(
    onOpenCategory: (Long, YearMonth, TransactionType) -> Unit,
    viewModel: BreakdownViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val current = state ?: return ScreenLoading()
    val locale = LocalConfiguration.current.locales[0]

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val empty = current.slices.isEmpty()
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.xl),
            verticalArrangement = if (empty) Arrangement.SpaceBetween else Arrangement.spacedBy(Spacing.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                ScreenTitle(stringResource(R.string.nav_breakdown), monthTitle("LLLL yyyy", current.month, locale))
                SeyirSegmented(
                    listOf(stringResource(R.string.add_expense), stringResource(R.string.add_income)),
                    types.indexOf(current.type),
                    { viewModel.setType(types[it]) },
                )
            }
            AnimatedContent(
                current,
                transitionSpec = { slideFade(types.indexOf(targetState.type) > types.indexOf(initialState.type)) },
                contentKey = { it.type },
                label = "breakdownType",
            ) { shown ->
                BreakdownBody(shown, locale, onOpenCategory = { onOpenCategory(it, shown.month, shown.type) })
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SubText(stringResource(R.string.breakdown_note), Modifier.padding(horizontal = Spacing.xs))
                if (!empty) SubText(stringResource(R.string.breakdown_trend_note), Modifier.padding(horizontal = Spacing.xs))
            }
        }
    }
}

@Composable
private fun BreakdownBody(current: BreakdownUiState, locale: Locale, onOpenCategory: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val income = current.type == TransactionType.INCOME
    val palette = LocalCategoryPalette.current
    val reducedMotion = LocalReducedMotion.current
    // Yeni içerik ilk çizildiğinde çubuklar 0'dan dolar (ADR-0016).
    var grown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { grown = true }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (current.slices.isEmpty()) {
            EmptyState(
                R.drawable.ic_seyir_breakdown,
                stringResource(if (income) R.string.breakdown_empty_income else R.string.breakdown_empty_expense),
                stringResource(R.string.breakdown_empty_body),
                Modifier.padding(vertical = Spacing.md),
            )
            return@Column
        }
        val chart = remember(current.slices) { donutSlices(current.slices) }
        SeyirCard {
            BreakdownDonut(
                chart, current.totalMinor,
                stringResource(if (income) R.string.breakdown_total_income else R.string.breakdown_total_expense),
                locale, palette, Modifier.align(Alignment.CenterHorizontally),
            )
        }
        val openLabel = stringResource(R.string.breakdown_open_category)
        SeyirCard(Modifier.animateContentSize(Motion.contentSize()), padding = PaddingValues(vertical = Spacing.xs)) {
            current.slices.forEachIndexed { index, slice ->
                key(slice.categoryId) {
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = colors.outlineVariant)
                    val accent = palette.categoryColor(slice.categoryId)
                    val fraction by animateFloatAsState(
                        if (grown || reducedMotion) current.barFraction(slice) else 0f,
                        if (reducedMotion) snap() else Motion.medium(),
                        label = "bar",
                    )
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .clickable(role = Role.Button, onClickLabel = openLabel) { onOpenCategory(slice.categoryId) }
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Box {
                            CategoryCircle(categoryIcon(slice.icon))
                            Box(Modifier.align(Alignment.BottomEnd).size(8.dp).background(accent, CircleShape))
                        }
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Text(
                                    categoryName(slice.key, slice.name), Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        formatMoney(slice.amountMinor, locale),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                                        textAlign = TextAlign.End,
                                    )
                                    val change = current.change(slice)
                                    val delta = when (change?.signum()) {
                                        null -> stringResource(R.string.trend_unavailable)
                                        -1 -> stringResource(R.string.trend_delta, "−", change.abs().toString())
                                        1 -> stringResource(R.string.trend_delta, "+", change.toString())
                                        else -> stringResource(R.string.trend_delta, "", change.toString())
                                    }
                                    val description = if (change == null) stringResource(R.string.breakdown_trend_unavailable)
                                        else stringResource(R.string.breakdown_trend_value, delta)
                                    SubText(delta, Modifier.clearAndSetSemantics { contentDescription = description }, textAlign = TextAlign.End)
                                }
                            }
                            Row(
                                Modifier.padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                Box(
                                    Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))
                                        .background(colors.surfaceContainerHigh)
                                        .drawBehind {
                                            val width = size.width * fraction
                                            if (width > 0f) {
                                                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
                                                drawRoundRect(accent, Offset(x, 0f), Size(width, size.height), CornerRadius(4.dp.toPx()))
                                            }
                                        },
                                )
                                SubText(stringResource(R.string.percent, current.percent(slice)), Modifier.widthIn(min = 34.dp), textAlign = TextAlign.End)
                            }
                            current.budgetLimits[slice.categoryId]?.let {
                                SubText(budgetAmounts(slice.amountMinor, it, locale), Modifier.padding(top = Spacing.xs))
                            }
                        }
                    }
                }
            }
        }
    }
}
