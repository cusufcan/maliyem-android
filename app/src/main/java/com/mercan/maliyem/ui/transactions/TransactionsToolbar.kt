package com.mercan.maliyem.ui.transactions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.mercan.maliyem.R
import com.mercan.maliyem.ui.accounts.accountName
import com.mercan.maliyem.ui.components.BackHeader
import com.mercan.maliyem.ui.components.ScreenTitle
import com.mercan.maliyem.ui.components.SeyirChip
import com.mercan.maliyem.ui.components.SeyirCompactControl
import com.mercan.maliyem.ui.components.categoryName
import com.mercan.maliyem.ui.components.monthTitle
import com.mercan.maliyem.ui.theme.SeyirIcon
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.util.formatMoney
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Arama açılınca başlığın yerini alan alan ve süzgeç eylemi. */
@Composable
internal fun TransactionsToolbar(
    search: String?,
    onSearch: (String?) -> Unit,
    onSubmitSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (search == null) {
            Box(Modifier.weight(1f)) {
                val title = stringResource(R.string.nav_transactions)
                if (onBack == null) ScreenTitle(title) else BackHeader(title, onBack)
            }
            SeyirCompactControl {
                IconButton(onClick = { onSearch("") }) {
                    SeyirIcon(R.drawable.ic_seyir_search, description = stringResource(R.string.transactions_search))
                }
            }
        } else {
            val focus = remember { FocusRequester() }
            OutlinedTextField(
                search, onSearch, Modifier.weight(1f).focusRequester(focus),
                label = { Text(stringResource(R.string.transactions_search)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
                trailingIcon = {
                    IconButton(onClick = { onSearch(null) }) {
                        SeyirIcon(R.drawable.ic_seyir_close, description = stringResource(R.string.transactions_search_close))
                    }
                },
            )
            LaunchedEffect(Unit) { focus.requestFocus() }
        }
        SeyirCompactControl {
            IconButton(onClick = onOpenFilters) {
                SeyirIcon(R.drawable.ic_seyir_filter, description = stringResource(R.string.transactions_filters))
            }
        }
    }
}

/** Etkin ölçütler tek tek kaldırılabilir; yatay kaydırma listenin alanını korur. */
@Composable
internal fun ActiveTransactionFilters(
    filters: TransactionsFilters,
    options: TransactionsOptions,
    month: YearMonth,
    locale: Locale,
    onChange: (TransactionsFilters) -> Unit,
) {
    if (!filters.isActive) return
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (filters.type != TransactionFilter.ALL) {
                ActiveFilterChip(stringResource(filters.type.labelRes)) { onChange(filters.copy(type = TransactionFilter.ALL)) }
            }
            if (filters.range != TransactionsRange.MONTH) {
                ActiveFilterChip(transactionRangeLabel(filters, month, locale)) {
                    onChange(filters.copy(range = TransactionsRange.MONTH, customStart = null, customEnd = null))
                }
            }
            options.categories.filter { it.id in filters.categoryIds }.forEach { category ->
                ActiveFilterChip(categoryName(category.key, category.name)) {
                    onChange(filters.copy(categoryIds = filters.categoryIds - category.id))
                }
            }
            options.accounts.filter { it.id in filters.accountIds }.forEach { account ->
                ActiveFilterChip(accountName(account)) { onChange(filters.copy(accountIds = filters.accountIds - account.id)) }
            }
            filters.minimum?.let {
                ActiveFilterChip(amountChipLabel(R.string.transactions_amount_min, it, locale)) { onChange(filters.copy(minimum = null)) }
            }
            filters.maximum?.let {
                ActiveFilterChip(amountChipLabel(R.string.transactions_amount_max, it, locale)) { onChange(filters.copy(maximum = null)) }
            }
        }
        SeyirChip(
            stringResource(R.string.transactions_filter_clear), selected = false,
            onClick = { onChange(TransactionsFilters()) }, role = Role.Button,
        )
    }
}

@Composable
private fun ActiveFilterChip(label: String, onRemove: () -> Unit) {
    val description = stringResource(R.string.transactions_filter_remove, label)
    SeyirChip(
        label, selected = true, onClick = onRemove, Modifier.semantics { contentDescription = description },
        icon = R.drawable.ic_seyir_close, role = Role.Button,
    )
}

@Composable
private fun amountChipLabel(label: Int, amount: Long, locale: Locale) =
    stringResource(R.string.transactions_amount_chip, stringResource(label), formatMoney(amount, locale))

@Composable
internal fun transactionRangeLabel(filters: TransactionsFilters, month: YearMonth, locale: Locale): String =
    when (filters.range) {
        TransactionsRange.MONTH -> monthTitle("LLLL yyyy", month, locale)
        TransactionsRange.CUSTOM -> {
            val format = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
            stringResource(
                R.string.transactions_date_span, requireNotNull(filters.customStart).format(format),
                requireNotNull(filters.customEnd).format(format),
            )
        }
        else -> stringResource(filters.range.labelRes)
    }
