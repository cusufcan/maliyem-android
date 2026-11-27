package com.mercan.maliyem.ui.summary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
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
import com.mercan.maliyem.data.repository.AccountBalance
import com.mercan.maliyem.ui.accounts.AccountsCard
import com.mercan.maliyem.ui.budgets.BudgetsCard
import com.mercan.maliyem.ui.components.EmptyState
import com.mercan.maliyem.ui.components.Eyebrow
import com.mercan.maliyem.ui.components.MonthHeader
import com.mercan.maliyem.ui.components.ScreenLoading
import com.mercan.maliyem.ui.components.SeyirActionButton
import com.mercan.maliyem.ui.components.SeyirButton
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SeyirFabClearance
import com.mercan.maliyem.ui.components.SubText
import com.mercan.maliyem.ui.components.TransactionRow
import com.mercan.maliyem.ui.components.categoryName
import com.mercan.maliyem.ui.components.monthTitle
import com.mercan.maliyem.ui.goals.GoalsCard
import com.mercan.maliyem.ui.theme.LocalMoneyColors
import com.mercan.maliyem.ui.theme.LocalReducedMotion
import com.mercan.maliyem.ui.theme.MaliyemTheme
import com.mercan.maliyem.ui.theme.MoneyHeroValue
import com.mercan.maliyem.ui.theme.Motion
import com.mercan.maliyem.ui.theme.SeyirIcon
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.ui.theme.countSlide
import com.mercan.maliyem.ui.theme.crossFade
import com.mercan.maliyem.ui.theme.slideFade
import com.mercan.maliyem.ui.trend.MonthlyTrend
import com.mercan.maliyem.ui.trend.trendMonths
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
    onOpenReceipt: (String) -> Unit,
    onAdd: () -> Unit,
    onOpenBudget: (Long?) -> Unit,
    onOpenGoals: () -> Unit,
    onOpenYearlyReport: () -> Unit,
    viewModel: SummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accounts by viewModel.activeAccounts.collectAsStateWithLifecycle()
    val goals by viewModel.featuredGoals.collectAsStateWithLifecycle()
    val goalsCard: @Composable () -> Unit = { GoalsCard(goals, onOpenGoals) }
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
        val openBudget: (Long?) -> Unit = {
            shown?.let { viewModel.selectMonth(it.month) }
            onOpenBudget(it)
        }
        when {
            shown == null -> ScreenLoading()
            first && shown.budgets.isEmpty -> FirstLaunch(onAdd, accounts, goalsCard)
            shown.isEmpty -> EmptyMonth(
                shown, viewModel::previousMonth, viewModel::nextMonth, viewModel::selectMonth, onAdd, onSeeAll, accounts, openBudget, goalsCard, onOpenYearlyReport,
            )
            else -> SummaryContent(
                shown, onPreviousMonth = viewModel::previousMonth, onNextMonth = viewModel::nextMonth, onSeeAll = onSeeAll,
                highlightId = highlightId, onOpen = onOpen, onOpenReceipt = onOpenReceipt, accounts = accounts, onOpenBudget = openBudget, onSelectMonth = viewModel::selectMonth,
                goalsCard = goalsCard, onOpenYearlyReport = onOpenYearlyReport,
            )
        }
    }
}

/** Pano 7 · İlk açılış. */
@Composable
private fun FirstLaunch(
    onAdd: () -> Unit,
    accounts: List<AccountBalance>,
    goalsCard: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.lg),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                AccountsCard(accounts)
                goalsCard()
            }
            EmptyState(
                R.drawable.ic_seyir_wallet,
                stringResource(R.string.empty_first_title),
                stringResource(R.string.empty_first_body),
            ) {
                SubText(stringResource(R.string.empty_first_note), textAlign = TextAlign.Center)
                SeyirButton(stringResource(R.string.empty_first_add), onAdd, icon = R.drawable.ic_seyir_plus)
            }
            Spacer(Modifier.height(0.dp))
        }
    }
}

/** Pano 7 · Yeni ay: kayıt yokken ay başlığı, çağrı ve geçen ayın kapanışı. */
@Composable
private fun EmptyMonth(
    state: SummaryUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onAdd: () -> Unit,
    onSeeAll: () -> Unit,
    accounts: List<AccountBalance>,
    onOpenBudget: (Long?) -> Unit,
    goalsCard: @Composable () -> Unit,
    onOpenYearlyReport: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val monthsIn = stringArrayResource(R.array.month_in)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = SeyirFabClearance),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                MonthHeader(
                    state.month, locale,
                    onPrevious = onPreviousMonth.takeIf { state.previousMonth != null },
                    onNext = onNextMonth.takeIf { state.nextMonth != null },
                )
                AccountsCard(accounts)
                BudgetsCard(state.budgets, locale, onOpenBudget)
                goalsCard()
            }
            EmptyState(
                R.drawable.ic_seyir_calendar,
                stringResource(
                    if (state.hasTransfers) R.string.empty_month_transfers_title else R.string.empty_month_title,
                    monthsIn[state.month.monthValue - 1],
                ),
                stringResource(R.string.empty_month_body),
                Modifier.padding(top = Spacing.lg),
            ) {
                SeyirButton(stringResource(R.string.add_open), onAdd, icon = R.drawable.ic_seyir_plus)
                if (state.hasTransfers) SeyirButton(stringResource(R.string.nav_transactions), onSeeAll, tonal = true)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                state.previousNet?.let { (previous, net) ->
                    val colors = MaterialTheme.colorScheme
                    SeyirCard(
                        padding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                        onClick = { onSelectMonth(previous) },
                    ) {
                        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
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
                SeyirTrendCard(state.trend, locale, onSelectMonth, onOpenYearlyReport)
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
    onOpenReceipt: (String) -> Unit = {},
    accounts: List<AccountBalance> = emptyList(),
    onOpenBudget: (Long?) -> Unit = {},
    onSelectMonth: (YearMonth) -> Unit = {},
    goalsCard: @Composable () -> Unit = {},
    onOpenYearlyReport: (() -> Unit)? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = SeyirFabClearance),
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
        AccountsCard(accounts)
        BudgetsCard(state.budgets, locale, onOpenBudget)
        goalsCard()
        SeyirTrendCard(state.trend, locale, onSelectMonth, onOpenYearlyReport)
        RecentSection(state, locale, onSeeAll, highlightId, onOpen, onOpenReceipt)
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
                if (state.overspent) stringResource(R.string.summary_overspent) else stringResource(R.string.summary_spent, it),
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
private fun RecentSection(state: SummaryUiState, locale: Locale, onSeeAll: () -> Unit, highlightId: Long?, onOpen: (Long) -> Unit, onOpenReceipt: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.xs, top = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Eyebrow(stringResource(R.string.summary_recent), Modifier.weight(1f).semantics { heading() })
        SeyirActionButton(stringResource(R.string.summary_see_all), onSeeAll)
    }
    Column(
        // Satır eklenip silinince kart boyu sıçramadan değişir.
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(colors.surfaceContainer),
    ) {
        val reducedMotion = LocalReducedMotion.current
        val rows = state.recent
        val currentIds = rows.mapTo(mutableSetOf()) { it.id }
        val previousRows = remember { mutableStateMapOf<Long, Pair<Int, SummaryRow>>() }
        SideEffect {
            rows.forEachIndexed { index, row -> previousRows[row.id] = index to row }
        }
        val rendered = rows.toMutableList()
        previousRows.values.filter { (_, row) -> row.id !in currentIds }.sortedBy { it.first }
            .forEach { (index, row) -> rendered.add(index.coerceAtMost(rendered.size), row) }
        rendered.forEachIndexed { index, row ->
            key(row.id) {
                val visible = row.id in currentIds
                val visibility = remember {
                    MutableTransitionState(visible && (reducedMotion || row.id != highlightId))
                }
                visibility.targetState = visible
                LaunchedEffect(visible, visibility.isIdle, visibility.currentState) {
                    if (!visible && visibility.isIdle && !visibility.currentState) previousRows.remove(row.id)
                }
                val enter = if (reducedMotion || row.id != highlightId) Motion.expand() else {
                    val offset = with(LocalDensity.current) { 6.dp.roundToPx() }
                    Motion.expand() + slideInVertically(Motion.enter()) { -offset }
                }
                AnimatedVisibility(visibility, enter = enter, exit = Motion.collapse()) {
                    Column {
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = colors.outlineVariant)
                        val category = categoryName(row.categoryKey, row.categoryName)
                        val income = row.type == TransactionType.INCOME
                        TransactionRow(
                            title = row.note ?: category,
                            subtitle = category.takeIf { row.note != null },
                            icon = row.icon,
                            amount = formatMoney(if (income) row.amountMinor else -row.amountMinor, locale, signed = true),
                            income = income,
                            recurring = row.recurring,
                            onOpen = { onOpen(row.id) },
                            onOpenReceipt = row.receiptPath?.let { path -> { onOpenReceipt(path) } },
                            highlighted = row.id == highlightId,
                            horizontalPadding = Spacing.lg,
                        )
                    }
                }
            }
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
            incomeMinor = 3_170_000.toBigInteger(),
            expenseMinor = 2_207_665.toBigInteger(),
            trend = trendMonths(YearMonth.of(2026, 11)).mapIndexed { index, month ->
                val income = listOf(2_850_000, 2_920_000, 0, 2_850_000, 2_850_000, 3_170_000)
                val expense = listOf(2_150_000, 1_920_000, 0, 2_300_000, 2_510_000, 2_207_665)
                MonthlyTrend(month, income[index].toBigInteger(), expense[index].toBigInteger())
            },
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
