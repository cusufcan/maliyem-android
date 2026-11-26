package com.mercan.maliyem.ui.summary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import com.mercan.maliyem.ui.theme.Motion
import com.mercan.maliyem.ui.theme.countSlide
import com.mercan.maliyem.ui.theme.crossFade
import com.mercan.maliyem.ui.theme.slideFade
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringArrayResource
import com.mercan.maliyem.ui.components.EmptyState
import com.mercan.maliyem.ui.components.Eyebrow
import com.mercan.maliyem.ui.components.MonthHeader
import com.mercan.maliyem.ui.components.SeyirButton
import com.mercan.maliyem.ui.components.SubText
import com.mercan.maliyem.ui.components.categoryName
import com.mercan.maliyem.ui.components.monthTitle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.mercan.maliyem.ui.components.TransactionRow
import com.mercan.maliyem.ui.theme.LocalMoneyColors
import com.mercan.maliyem.ui.theme.MaliyemTheme
import com.mercan.maliyem.ui.theme.MoneyHeroValue
import com.mercan.maliyem.ui.theme.SeyirIcon
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.util.formatMoney
import com.mercan.maliyem.util.splitFraction
import java.time.YearMonth
import java.util.Locale

/**
 * Özet sekmesi. Ekle düğmesi, alt sayfa ve snackbar uygulama düzeyinde (ADR-0010); burada yalnız içerik çizilir.
 * [firstLaunch] hiç kayıt yokken ilk açılış boş durumunu gösterir.
 */
@Composable
fun SummaryScreen(
    highlightId: Long?,
    firstLaunch: Boolean,
    onSeeAll: () -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    viewModel: SummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // İlk açılış, boş ay ve dolu ay arası solarak; ay değişimi yönlü kayarak geçer (ADR-0016).
    AnimatedContent(
        firstLaunch to state,
        Modifier.fillMaxSize(),
        transitionSpec = {
            val from = initialState.second
            val to = targetState.second
            if (!initialState.first && !targetState.first && from != null && to != null && from.month != to.month) slideFade(to.month > from.month)
            else crossFade()
        },
        contentKey = { (first, s) -> if (first) "first" else s?.let { it.month to it.isEmpty } },
        label = "summary",
    ) { (first, shown) ->
        when {
            first -> FirstLaunch(onAdd)
            shown == null -> Unit
            shown.isEmpty -> EmptyMonth(shown, onPreviousMonth = viewModel::previousMonth, onNextMonth = viewModel::nextMonth, onAdd = onAdd)
            else -> SummaryContent(
                shown, onPreviousMonth = viewModel::previousMonth, onNextMonth = viewModel::nextMonth, onSeeAll = onSeeAll,
                highlightId = highlightId, onOpen = onOpen,
            )
        }
    }
}

/** Pano 7 · İlk açılış. */
@Composable
private fun FirstLaunch(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.lg),
    ) {
        EmptyState(
            R.drawable.ic_seyir_wallet,
            stringResource(R.string.empty_first_title),
            stringResource(R.string.empty_first_body),
            Modifier.weight(1f),
        ) {
            SubText(stringResource(R.string.empty_first_note), Modifier.padding(top = 0.dp))
            SeyirButton(stringResource(R.string.empty_first_add), onAdd, icon = R.drawable.ic_seyir_plus)
        }
    }
}

/** Pano 7 · Yeni ay: kayıt yokken ay başlığı, çağrı ve geçen ayın kapanışı. */
@Composable
private fun EmptyMonth(state: SummaryUiState, onPreviousMonth: () -> Unit, onNextMonth: () -> Unit, onAdd: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val monthsIn = stringArrayResource(R.array.month_in)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        MonthHeader(
            state.month, locale,
            onPrevious = onPreviousMonth.takeIf { state.previousMonth != null },
            onNext = onNextMonth.takeIf { state.nextMonth != null },
        )
        EmptyState(
            R.drawable.ic_seyir_calendar,
            stringResource(R.string.empty_month_title, monthsIn[state.month.monthValue - 1]),
            stringResource(R.string.empty_month_body),
            Modifier.weight(1f),
        ) {
            SeyirButton(stringResource(R.string.add_open), onAdd, icon = R.drawable.ic_seyir_plus)
        }
        val previous = state.previousMonth
        val net = state.previousNet
        if (previous != null && net != null) {
            val colors = MaterialTheme.colorScheme
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(MaterialTheme.shapes.large)
                    .background(colors.surfaceContainer).clickable(onClick = onPreviousMonth)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    SubText(stringResource(R.string.empty_month_last))
                    Text(
                        stringResource(R.string.empty_month_closed, monthTitle("LLLL", previous, locale), formatMoney(net, locale, signed = true)),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                    )
                }
                SeyirIcon(R.drawable.ic_seyir_chevron_right, tint = colors.onSurfaceVariant)
            }
        }
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
    onOpen: (Long) -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            // Alttaki 88 dp (pano .scr) son satırın Ekle düğmesinin altında kalmaması için.
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = 88.dp),
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
        RecentSection(state, locale, onSeeAll, highlightId, onOpen)
    }
}

@Composable
private fun Hero(state: SummaryUiState, locale: Locale) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().background(colors.primaryContainer, MaterialTheme.shapes.extraLarge).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Eyebrow(stringResource(R.string.summary_net, monthTitle("LLLL", state.month, locale)), color = colors.onPrimaryContainer)
        // Kaydet ya da geri al sonrası net tutar artınca aşağıdan, azalınca yukarıdan kayarak güncellenir.
        AnimatedContent(state.netMinor, transitionSpec = { countSlide(targetState > initialState) }, label = "heroNet") { net ->
            BasicText(
                heroAmount(formatMoney(net, locale, signed = true), locale),
                style = MoneyHeroValue.copy(color = colors.onPrimaryContainer),
                maxLines = 1,
                // Uzun tutar ve büyük yazı boyunda taşmak yerine küçülür.
                autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = MoneyHeroValue.fontSize, stepSize = 2.sp),
            )
        }
        state.spentPercent?.let {
            Text(
                // %100'ü geçen oran okunmaz; o zaman yalnız aşıldığı söylenir.
                if (it > 100) stringResource(R.string.summary_overspent) else stringResource(R.string.summary_spent, it),
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
        AnimatedContent(amount, transitionSpec = { crossFade() }, label = "tileAmount") { shown ->
            Text(
                shown, color = content, maxLines = 1,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-.01).em),
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 20.sp, stepSize = 1.sp),
            )
        }
    }
}

@Composable
private fun RecentSection(state: SummaryUiState, locale: Locale, onSeeAll: () -> Unit, highlightId: Long?, onOpen: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.xs, top = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Eyebrow(stringResource(R.string.summary_recent), Modifier.weight(1f).semantics { heading() })
        TextButton(onSeeAll) {
            Text(stringResource(R.string.summary_see_all), fontWeight = FontWeight.SemiBold)
        }
    }
    Column(
        // Satır eklenip silinince kart boyu sıçramadan değişir.
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(colors.surfaceContainer).animateContentSize(Motion.enter()),
    ) {
        state.recent.forEachIndexed { index, row -> key(row.id) {
            // Pano "nw": yeni kaydedilen satır yukarıdan 6 dp kayarak ve solarak gelir (200 ms).
            val appear = remember { Animatable(if (row.id == highlightId) 0f else 1f) }
            LaunchedEffect(Unit) { appear.animateTo(1f, Motion.enter()) }
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = colors.outlineVariant)
            val category = categoryName(row.categoryKey, row.categoryName)
            val income = row.type == TransactionType.INCOME
            TransactionRow(
                title = row.note ?: category,
                subtitle = category.takeIf { row.note != null },
                icon = row.icon,
                amount = formatMoney(if (income) row.amountMinor else -row.amountMinor, locale, signed = true),
                income = income,
                modifier = Modifier
                    .graphicsLayer { alpha = appear.value; translationY = (appear.value - 1f) * 6.dp.toPx() }
                    .clickable { onOpen(row.id) },
                highlighted = row.id == highlightId,
                horizontalPadding = Spacing.lg,
            )
        } }
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
