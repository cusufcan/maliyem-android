package com.mercan.maliyem.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mercan.maliyem.R
import com.mercan.maliyem.ui.accounts.accountName
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.ui.components.EmptyState
import com.mercan.maliyem.ui.components.Eyebrow
import com.mercan.maliyem.ui.components.FilterChips
import com.mercan.maliyem.ui.components.ScreenTitle
import com.mercan.maliyem.ui.components.SubText
import com.mercan.maliyem.ui.components.TransactionRow
import com.mercan.maliyem.ui.components.categoryName
import com.mercan.maliyem.ui.components.monthTitle
import com.mercan.maliyem.ui.theme.Motion
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.util.formatMoney
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TransactionsScreen(
    highlightId: Long?,
    onOpen: (Long) -> Unit,
    onOpenTransfer: (Long) -> Unit,
    highlightTransferId: Long?,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val current = state ?: return
    val locale = LocalConfiguration.current.locales[0]
    val dayFormat = remember(locale) { DateTimeFormatter.ofPattern("d MMMM EEEE", locale) }
    val filters = listOf(R.string.filter_all, R.string.filter_income, R.string.filter_expense).map { stringResource(it) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = 88.dp),
    ) {
        item {
            ScreenTitle(
                stringResource(R.string.nav_transactions),
                stringResource(
                    R.string.transactions_subtitle,
                    monthTitle("LLLL yyyy", current.month, locale),
                    pluralStringResource(R.plurals.records, current.count, current.count),
                ),
            )
        }
        item {
            FilterChips(
                filters, current.filter.ordinal, { viewModel.setFilter(TransactionFilter.entries[it]) },
                Modifier.padding(top = Spacing.md),
            )
        }
        if (current.days.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    R.drawable.ic_seyir_calendar,
                    stringResource(R.string.transactions_empty_title),
                    stringResource(R.string.transactions_empty_body),
                    Modifier.animateItem(fadeInSpec = Motion.medium(), fadeOutSpec = Motion.fast()).padding(top = Spacing.xl),
                )
            }
        }
        current.days.forEach { day ->
            item(key = "day-${current.filter}-${day.date.toEpochDay()}", contentType = "day") {
                Row(
                    Modifier.fillMaxWidth().animateItem(fadeInSpec = Motion.medium(), fadeOutSpec = Motion.fast())
                        .padding(start = Spacing.xs, end = Spacing.xs, top = Spacing.md + Spacing.sm, bottom = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Eyebrow(dayLabel(day.date, today, dayFormat), Modifier.weight(1f).semantics { heading() })
                    if (day.rows.any { it is DayRow.Record }) SubText(formatMoney(day.netMinor, locale, signed = true))
                }
            }
            itemsIndexed(day.rows, key = { _, row -> "${row is DayRow.Transfer}-${row.id}" }, contentType = { _, _ -> "record" }) { index, row ->
                val top = if (index == 0) 20.dp else 0.dp
                val bottom = if (index == day.rows.lastIndex) 20.dp else 0.dp
                val colors = MaterialTheme.colorScheme
                Column(
                    Modifier.animateItem(fadeInSpec = Motion.medium(), fadeOutSpec = Motion.fast())
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
                                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(row.id) },
                                highlighted = row.id == highlightId,
                                horizontalPadding = Spacing.lg,
                            )
                        }
                        is DayRow.Transfer -> {
                            val transfer = row.item
                            TransactionRow(
                                title = stringResource(
                                    R.string.transfer_direction,
                                    accountName(current.accounts, transfer.fromAccountId),
                                    accountName(current.accounts, transfer.toAccountId),
                                ),
                                subtitle = transfer.note ?: stringResource(R.string.add_transfer),
                                icon = "transfer",
                                amount = formatMoney(transfer.amountMinor, locale),
                                income = false,
                                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenTransfer(row.id) },
                                highlighted = row.id == highlightTransferId,
                                horizontalPadding = Spacing.lg,
                            )
                        }
                    }
                }
            }
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
