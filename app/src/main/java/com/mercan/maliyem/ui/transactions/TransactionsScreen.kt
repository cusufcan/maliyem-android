package com.mercan.maliyem.ui.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.DefaultCategories
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.ui.accounts.accountName
import com.mercan.maliyem.ui.components.EmptyState
import com.mercan.maliyem.ui.components.Eyebrow
import com.mercan.maliyem.ui.components.MonthHeader
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SeyirFabClearance
import com.mercan.maliyem.ui.components.SubText
import com.mercan.maliyem.ui.components.TransactionRow
import com.mercan.maliyem.ui.components.categoryName
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.ui.theme.seyirAnimateItem
import com.mercan.maliyem.util.formatMoney
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionsScreen(
    highlightId: Long?,
    onOpen: (Long) -> Unit,
    onOpenReceipt: (String) -> Unit,
    onOpenTransfer: (Long) -> Unit,
    highlightTransferId: Long?,
    onBack: (() -> Unit)? = null,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val result by viewModel.result.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    var sheetSession by rememberSaveable { mutableStateOf<String?>(null) }
    val sheetOpen = sheetSession == transactionsSession
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    var imeWasVisible by remember { mutableStateOf(false) }
    var keepSearchOnHide by remember { mutableStateOf(false) }
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(resources, locale) {
        val labels = DefaultCategories.all.map { it.nameRes } + R.string.account_cash
        viewModel.setLocalizedNames(labels.associateWith { resources.getString(it) })
    }
    val (filters, options) = selection ?: return
    val currentPeriod = period ?: return
    val dayFormat = remember(locale) { DateTimeFormatter.ofPattern("d MMMM EEEE", locale) }
    val rangeResults = search != null || filters.range != TransactionsRange.MONTH

    fun setSearch(text: String?) {
        if (text == null) keyboard?.hide()
        viewModel.setSearch(text)
    }
    // Klavye açıkken ilk Geri olayını klavye alır; bu durumda alanı da kapatırız.
    LaunchedEffect(imeVisible, search != null) {
        val backFromKeyboard = search != null && imeWasVisible && !imeVisible && !keepSearchOnHide
        imeWasVisible = search != null && imeVisible
        if (!imeVisible || search == null) keepSearchOnHide = false
        if (backFromKeyboard && !sheetOpen) setSearch(null)
    }
    BackHandler(enabled = search != null && !sheetOpen) { setSearch(null) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            TransactionsToolbar(
                search, ::setSearch,
                onBack = onBack,
                onSubmitSearch = {
                    keepSearchOnHide = imeVisible
                    keyboard?.hide()
                },
                onOpenFilters = {
                    keepSearchOnHide = imeVisible
                    keyboard?.hide()
                    sheetSession = transactionsSession
                },
            )
            ActiveTransactionFilters(filters, options, currentPeriod.month, locale, viewModel::setFilters)
        }
        TransactionsList(
            result, filters, options, currentPeriod, rangeResults, locale, dayFormat,
            viewModel::selectMonth, highlightId, highlightTransferId, onOpen, onOpenReceipt, onOpenTransfer,
        )
    }
    if (sheetOpen) {
        key(transactionsSession) {
            TransactionsFilterSheet(filters, options, currentPeriod, locale, onDismiss = { sheetSession = null }) {
                viewModel.setFilters(it)
                sheetSession = null
            }
        }
    }
}

@Composable
private fun TransactionsList(
    result: TransactionsResult?,
    filters: TransactionsFilters,
    options: TransactionsOptions,
    period: TransactionsPeriod,
    rangeResults: Boolean,
    locale: Locale,
    dayFormat: DateTimeFormatter,
    onMonth: (YearMonth) -> Unit,
    highlightId: Long?,
    highlightTransferId: Long?,
    onOpen: (Long) -> Unit,
    onOpenReceipt: (String) -> Unit,
    onOpenTransfer: (Long) -> Unit,
) {
    val count = result?.let { pluralStringResource(R.plurals.records, it.count, it.count) }
    var headerHeight by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = SeyirFabClearance),
        ) {
            item(key = "period") {
                Box(seyirAnimateItem().onSizeChanged { headerHeight = it.height }) {
                    if (rangeResults) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text(
                                transactionRangeLabel(filters, period.month, locale),
                                Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge,
                            )
                            if (result != null && count != null) {
                                SeyirCard {
                                    SubText(count)
                                    SubText(stringResource(
                                        R.string.transactions_total, stringResource(R.string.breakdown_total_expense), formatMoney(result.expenseMinor, locale),
                                    ))
                                    SubText(stringResource(
                                        R.string.transactions_total, stringResource(R.string.breakdown_total_income), formatMoney(result.incomeMinor, locale),
                                    ))
                                }
                            }
                        }
                    } else {
                        MonthHeader(period.month, locale, period.previous?.let { { onMonth(it) } }, period.next?.let { { onMonth(it) } }, count)
                    }
                }
            }
            result?.days.orEmpty().forEach { day ->
                item(key = "header-${day.date}", contentType = "day") {
                    Row(
                        seyirAnimateItem().fillMaxWidth()
                            .padding(start = Spacing.xs, end = Spacing.xs, top = Spacing.md + Spacing.sm, bottom = Spacing.md),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Eyebrow(dayLabel(day.date, period.today, dayFormat), Modifier.weight(1f).semantics { heading() })
                        if (day.rows.any { it is DayRow.Record }) SubText(formatMoney(day.netMinor, locale, signed = true))
                    }
                }
                itemsIndexed(day.rows, key = { _, row -> "${row is DayRow.Transfer}-${row.id}" }, contentType = { _, _ -> "record" }) { index, row ->
                    val top = if (index == 0) 20.dp else 0.dp
                    val bottom = if (index == day.rows.lastIndex) 20.dp else 0.dp
                    val colors = MaterialTheme.colorScheme
                    Column(
                        seyirAnimateItem()
                            .fillMaxWidth().clip(RoundedCornerShape(top, top, bottom, bottom))
                            .background(colors.surfaceContainer),
                    ) {
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = colors.outlineVariant)
                        when (row) {
                            is DayRow.Record -> {
                                val (transaction, info) = row.item
                                val category = categoryName(info.key, info.name)
                                val income = transaction.type == TransactionType.INCOME
                                TransactionRow(
                                    title = transaction.note ?: category,
                                    subtitle = category.takeIf { transaction.note != null },
                                    icon = info.icon,
                                    amount = formatMoney(if (income) transaction.amountMinor else -transaction.amountMinor, locale, signed = true),
                                    income = income,
                                    recurring = transaction.recurringRuleId != null,
                                    onOpen = { onOpen(row.id) },
                                    onOpenReceipt = transaction.receiptPath?.let { path -> { onOpenReceipt(path) } },
                                    highlighted = row.id == highlightId,
                                    horizontalPadding = Spacing.lg,
                                )
                            }
                            is DayRow.Transfer -> {
                                val transfer = row.item
                                TransactionRow(
                                    title = stringResource(
                                        R.string.transfer_direction,
                                        accountName(options.accounts, transfer.fromAccountId),
                                        accountName(options.accounts, transfer.toAccountId),
                                    ),
                                    subtitle = transfer.note ?: stringResource(R.string.add_transfer),
                                    icon = "transfer",
                                    amount = formatMoney(transfer.amountMinor, locale),
                                    income = false,
                                    onOpen = { onOpenTransfer(row.id) },
                                    highlighted = row.id == highlightTransferId,
                                    horizontalPadding = Spacing.lg,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (result?.days?.isEmpty() == true) {
            val filtered = filters.isActive || rangeResults
            val header = with(LocalDensity.current) { headerHeight.toDp() }
            EmptyState(
                if (filtered) R.drawable.ic_seyir_search else R.drawable.ic_seyir_calendar,
                stringResource(if (filtered) R.string.transactions_no_matches else R.string.transactions_empty_title),
                stringResource(if (filtered) R.string.transactions_no_matches_body else R.string.transactions_empty_body),
                Modifier.fillMaxSize().padding(
                    start = Spacing.lg, end = Spacing.lg, top = header + Spacing.sm, bottom = SeyirFabClearance,
                ),
            )
        }
    }
}

/** Pano .dh: "Bugün", "Dün", yoksa "24 Kasım Salı". */
@Composable
private fun dayLabel(date: LocalDate, today: LocalDate?, formatter: DateTimeFormatter): String = when (date) {
    today -> stringResource(R.string.add_today)
    today?.minusDays(1) -> stringResource(R.string.yesterday)
    else -> date.format(formatter)
}
