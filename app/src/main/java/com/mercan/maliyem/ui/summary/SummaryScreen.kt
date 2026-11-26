package com.mercan.maliyem.ui.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.DefaultCategories
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.ui.add.AddTransactionSheet
import com.mercan.maliyem.ui.components.TransactionRow
import com.mercan.maliyem.ui.theme.LocalMoneyColors
import com.mercan.maliyem.ui.theme.MaliyemTheme
import com.mercan.maliyem.ui.theme.MoneyHeroValue
import com.mercan.maliyem.ui.theme.SeyirIcon
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.util.formatMoney
import com.mercan.maliyem.util.splitFraction
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun SummaryScreen(onSeeAll: () -> Unit, viewModel: SummaryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Alt sayfa ayrı rota değil, bu ekranın durumu (ADR-0008).
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var highlightId by rememberSaveable { mutableStateOf<Long?>(null) }
    val savedExpense = stringResource(R.string.add_saved_expense)
    val savedIncome = stringResource(R.string.add_saved_income)
    val undoLabel = stringResource(R.string.add_undo)
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { sheetOpen = true },
                icon = { SeyirIcon(R.drawable.ic_seyir_plus) },
                text = { Text(stringResource(R.string.add_open), fontWeight = FontWeight.SemiBold) },
                shape = MaterialTheme.shapes.medium,
            )
        },
    ) { padding ->
        state?.let {
            SummaryContent(
                it, onPreviousMonth = viewModel::previousMonth, onNextMonth = viewModel::nextMonth, onSeeAll = onSeeAll,
                modifier = Modifier.padding(padding), highlightId = highlightId,
            )
        }
    }
    if (sheetOpen) {
        AddTransactionSheet(
            onDismiss = { sheetOpen = false },
            onSaved = { entry ->
                sheetOpen = false
                viewModel.showMonth(entry.date)
                highlightId = entry.id
                scope.launch {
                    val message = if (entry.type == TransactionType.INCOME) savedIncome else savedExpense
                    val result = snackbar.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undo(entry.id)
                    highlightId = null
                }
            },
        )
    }
}

@Composable
fun SummaryContent(
    state: SummaryUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
    highlightId: Long? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            // Alttaki ek boşluk son satırın Ekle düğmesinin altında kalmaması için.
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.lg + 72.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        MonthHeader(
            state.month, locale,
            onPrevious = onPreviousMonth.takeIf { state.previousMonth != null },
            onNext = onNextMonth.takeIf { state.nextMonth != null },
        )
        Hero(state, locale)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            val money = LocalMoneyColors.current
            Tile(
                R.drawable.ic_seyir_arrow_in, stringResource(R.string.summary_income), formatMoney(state.incomeMinor, locale),
                money.incomeContainer, money.onIncomeContainer, Modifier.weight(1f),
            )
            Tile(
                R.drawable.ic_seyir_arrow_out, stringResource(R.string.summary_expense), formatMoney(state.expenseMinor, locale),
                MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f),
            )
        }
        RecentSection(state, locale, onSeeAll, highlightId)
    }
}

private fun monthTitle(pattern: String, month: YearMonth, locale: Locale) =
    DateTimeFormatter.ofPattern(pattern, locale).format(month).replaceFirstChar { it.titlecase(locale) }

@Composable
private fun MonthHeader(month: YearMonth, locale: Locale, onPrevious: (() -> Unit)?, onNext: (() -> Unit)?) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    // Gidilecek ay yoksa düğme pasif; M3'ün pasif içerik saydamlığı (%38).
    fun tintFor(action: (() -> Unit)?) = if (action != null) tint else tint.copy(alpha = .38f)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        // İkon kenarla hizalansın diye 48 dp düğme 12 dp dışarı taşar (pano: margin −12).
        IconButton(onPrevious ?: {}, Modifier.offset(x = (-12).dp), enabled = onPrevious != null) {
            SeyirIcon(R.drawable.ic_seyir_chevron_left, description = stringResource(R.string.summary_previous_month), tint = tintFor(onPrevious))
        }
        Text(
            monthTitle("LLLL yyyy", month, locale),
            Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
            textAlign = TextAlign.Center,
        )
        IconButton(onNext ?: {}, Modifier.offset(x = 12.dp), enabled = onNext != null) {
            SeyirIcon(R.drawable.ic_seyir_chevron_right, description = stringResource(R.string.summary_next_month), tint = tintFor(onNext))
        }
    }
}

@Composable
private fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) = Text(
    text, modifier, color = color,
    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = .02.em),
)

@Composable
private fun Hero(state: SummaryUiState, locale: Locale) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().background(colors.primaryContainer, MaterialTheme.shapes.extraLarge).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Eyebrow(stringResource(R.string.summary_net, monthTitle("LLLL", state.month, locale)), color = colors.onPrimaryContainer)
        BasicText(
            heroAmount(formatMoney(state.netMinor, locale, signed = true), locale),
            style = MoneyHeroValue.copy(color = colors.onPrimaryContainer),
            maxLines = 1,
            // Uzun tutar ve büyük yazı boyunda taşmak yerine küçülür.
            autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = MoneyHeroValue.fontSize, stepSize = 2.sp),
        )
        state.spentPercent?.let {
            Text(
                stringResource(R.string.summary_spent, it),
                color = colors.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

/** Kuruş ve ₺ kısmı büyük sayının 24/64'ü boyunda; autoSize küçültünce oran korunur. */
private fun heroAmount(formatted: String, locale: Locale): AnnotatedString {
    val (whole, fraction) = splitFraction(formatted, locale)
    return buildAnnotatedString {
        append(whole)
        withStyle(SpanStyle(fontSize = .375.em, letterSpacing = (-.01).em)) { append(fraction) }
    }
}

@Composable
private fun Tile(icon: Int, label: String, amount: String, background: Color, content: Color, modifier: Modifier) {
    Column(
        modifier.background(background, MaterialTheme.shapes.large).padding(horizontal = Spacing.lg, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SeyirIcon(icon, size = 16.dp, tint = content)
            Text(label, color = content, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        Text(
            amount, color = content, maxLines = 1,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-.01).em),
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 20.sp, stepSize = 1.sp),
        )
    }
}

@Composable
private fun RecentSection(state: SummaryUiState, locale: Locale, onSeeAll: () -> Unit, highlightId: Long?) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.xs, top = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Eyebrow(stringResource(R.string.summary_recent), Modifier.weight(1f).semantics { heading() })
        if (!state.isEmpty) {
            TextButton(onSeeAll) {
                Text(stringResource(R.string.summary_see_all), fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(colors.surfaceContainer),
    ) {
        if (state.isEmpty) {
            Box(Modifier.fillMaxWidth().padding(vertical = Spacing.xl), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.summary_empty), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        state.recent.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = colors.outlineVariant)
            val category = DefaultCategories.nameRes(row.categoryKey)?.let { stringResource(it) } ?: row.categoryName.orEmpty()
            val income = row.type == TransactionType.INCOME
            TransactionRow(
                title = row.note ?: category,
                subtitle = category.takeIf { row.note != null },
                icon = row.icon,
                amount = formatMoney(if (income) row.amountMinor else -row.amountMinor, locale, signed = true),
                income = income,
                highlighted = row.id == highlightId,
                horizontalPadding = Spacing.lg,
            )
        }
    }
}

@Preview(showBackground = true, locale = "tr")
@Composable
private fun SummaryPreview() = MaliyemTheme {
    SummaryContent(
        SummaryUiState(
            month = YearMonth.of(2026, 11),
            previousMonth = YearMonth.of(2026, 9),
            incomeMinor = 3_170_000,
            expenseMinor = 2_207_665,
            recent = listOf(
                SummaryRow(21, TransactionType.EXPENSE, 41_285, "Migros", "groceries", null, "cart"),
                SummaryRow(20, TransactionType.EXPENSE, 60_000, "Berber", DefaultCategories.OTHER_EXPENSE, null, "dots"),
                SummaryRow(17, TransactionType.INCOME, 320_000, "Logo tasarımı", "freelance", null, "laptop"),
            ),
        ),
        onPreviousMonth = {}, onNextMonth = {}, onSeeAll = {},
        highlightId = 21,
    )
}
