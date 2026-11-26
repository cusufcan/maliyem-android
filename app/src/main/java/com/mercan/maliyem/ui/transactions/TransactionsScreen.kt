package com.mercan.maliyem.ui.transactions

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.ui.components.EmptyState
import com.mercan.maliyem.ui.components.Eyebrow
import com.mercan.maliyem.ui.components.FilterChips
import com.mercan.maliyem.ui.components.ListCard
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
import java.util.Locale

@Composable
fun TransactionsScreen(
    highlightId: Long?,
    onOpen: (Long) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val current = state ?: return
    val locale = LocalConfiguration.current.locales[0]
    val filters = listOf(R.string.filter_all, R.string.filter_income, R.string.filter_expense).map { stringResource(it) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
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
            FilterChips(filters, current.filter.ordinal, { viewModel.setFilter(TransactionFilter.entries[it]) })
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
        // Anahtarda süzgeç de var: süzgeç ya da ay değişince gün blokları solarak yenilenir; silme/geri almada
        // aynı anahtarlı bloklar yerinde kayar ve boyu yumuşakça değişir (ADR-0016).
        items(current.days, key = { "${current.filter}-${it.date.toEpochDay()}" }) { day ->
            Column(
                Modifier.animateItem(fadeInSpec = Motion.medium(), fadeOutSpec = Motion.fast()).animateContentSize(Motion.enter()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = Spacing.xs, end = Spacing.xs, top = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Eyebrow(dayLabel(day.date, locale), Modifier.semantics { heading() })
                    SubText(formatMoney(day.netMinor, locale, signed = true))
                }
                ListCard(day.rows) { row ->
                    val category = categoryName(row.categoryKey, row.categoryName)
                    val income = row.type == TransactionType.INCOME
                    TransactionRow(
                        title = row.note ?: category,
                        subtitle = category.takeIf { row.note != null },
                        icon = row.icon,
                        amount = formatMoney(if (income) row.amountMinor else -row.amountMinor, locale, signed = true),
                        income = income,
                        modifier = Modifier.clickable { onOpen(row.id) },
                        highlighted = row.id == highlightId,
                        horizontalPadding = Spacing.lg,
                    )
                }
            }
        }
    }
}

/** Pano .dh: "Bugün", "Dün", yoksa "24 Kasım Salı". */
@Composable
private fun dayLabel(date: LocalDate, locale: Locale): String {
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.add_today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> date.format(DateTimeFormatter.ofPattern("d MMMM EEEE", locale))
    }
}
