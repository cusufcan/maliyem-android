package com.mercan.maliyem.ui.transactions

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mercan.maliyem.R
import com.mercan.maliyem.ui.accounts.accountName
import com.mercan.maliyem.ui.components.DateSelectionDialog
import com.mercan.maliyem.ui.components.Eyebrow
import com.mercan.maliyem.ui.components.SettingRow
import com.mercan.maliyem.ui.components.SeyirButton
import com.mercan.maliyem.ui.components.SeyirChip
import com.mercan.maliyem.ui.components.categoryName
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.ui.theme.categoryIcon
import com.mercan.maliyem.util.formatMoney
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TransactionsFilterSheet(
    initial: TransactionsFilters,
    options: TransactionsOptions,
    period: TransactionsPeriod,
    locale: Locale,
    onDismiss: () -> Unit,
    onApply: (TransactionsFilters) -> Unit,
) {
    var draft by rememberSaveable(stateSaver = Saver(save = { it.toBundle() }, restore = ::readFilters)) { mutableStateOf(initial) }
    var minimum by rememberSaveable { mutableStateOf(initial.minimum?.let { formatMoney(it, locale) }.orEmpty()) }
    var maximum by rememberSaveable { mutableStateOf(initial.maximum?.let { formatMoney(it, locale) }.orEmpty()) }
    var datePicker by rememberSaveable { mutableStateOf<Int?>(null) }
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val start = draft.customStart ?: period.month.atDay(1)
    val end = draft.customEnd ?: period.month.atEndOfMonth()
    val minMinor = parseTransactionAmountBound(minimum, locale)
    val maxMinor = parseTransactionAmountBound(maximum, locale)
    val invalidMin = minimum.isNotBlank() && minMinor == null
    val invalidMax = maximum.isNotBlank() && maxMinor == null
    val reversed = minMinor != null && maxMinor != null && minMinor > maxMinor

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = Spacing.lg)) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    stringResource(R.string.transactions_filters), Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                )
                Eyebrow(stringResource(R.string.transactions_filter_type))
                FilterOptions(Modifier.selectableGroup()) {
                    TransactionFilter.entries.forEach { type ->
                        SeyirChip(stringResource(type.labelRes), draft.type == type, {
                            val ids = options.categories.filter { type.transactionType == null || it.type == type.transactionType }
                                .map { it.id }.toSet()
                            draft = draft.copy(
                                type = type,
                                categoryIds = if (type == TransactionFilter.TRANSFER) emptySet() else draft.categoryIds.intersect(ids),
                            )
                        })
                    }
                }
                if (draft.type != TransactionFilter.TRANSFER) {
                    Eyebrow(stringResource(R.string.settings_categories))
                    FilterOptions {
                        options.categories.filter { draft.type.transactionType == null || it.type == draft.type.transactionType }.forEach { category ->
                            SeyirChip(
                                categoryName(category.key, category.name), category.id in draft.categoryIds,
                                { draft = draft.copy(categoryIds = draft.categoryIds.toggle(category.id)) },
                                icon = categoryIcon(category.icon), role = Role.Checkbox,
                            )
                        }
                    }
                }
                if (options.accounts.size >= 2) {
                    Eyebrow(stringResource(R.string.settings_accounts))
                    FilterOptions {
                        options.accounts.forEach { account ->
                            SeyirChip(
                                accountName(account), account.id in draft.accountIds,
                                { draft = draft.copy(accountIds = draft.accountIds.toggle(account.id)) },
                                icon = categoryIcon(account.icon), role = Role.Checkbox,
                            )
                        }
                    }
                }
                Eyebrow(stringResource(R.string.transactions_filter_date))
                FilterOptions(Modifier.selectableGroup()) {
                    TransactionsRange.entries.forEach { range ->
                        SeyirChip(stringResource(range.labelRes), draft.range == range, { draft = draft.copy(range = range) })
                    }
                }
                if (draft.range == TransactionsRange.CUSTOM) {
                    SettingRow(
                        R.drawable.ic_seyir_calendar, stringResource(R.string.transactions_date_start),
                        value = start.format(dateFormat), onClick = { datePicker = R.string.transactions_date_start }, horizontalPadding = 0.dp,
                    )
                    SettingRow(
                        R.drawable.ic_seyir_calendar, stringResource(R.string.transactions_date_end),
                        value = end.format(dateFormat), onClick = { datePicker = R.string.transactions_date_end }, horizontalPadding = 0.dp,
                    )
                }
                Eyebrow(stringResource(R.string.add_amount))
                AmountBoundField(minimum, { minimum = it }, R.string.transactions_amount_min, invalidMin)
                AmountBoundField(maximum, { maximum = it }, R.string.transactions_amount_max, invalidMax, reversed)
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onApply(TransactionsFilters()) }, Modifier.heightIn(min = 44.dp)) {
                    Text(stringResource(R.string.transactions_filter_clear))
                }
                SeyirButton(
                    stringResource(R.string.transactions_filter_apply),
                    onClick = {
                        val custom = draft.range == TransactionsRange.CUSTOM
                        onApply(draft.copy(
                            customStart = start.takeIf { custom }, customEnd = end.takeIf { custom },
                            minimum = minMinor, maximum = maxMinor,
                        ))
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !invalidMin && !invalidMax && !reversed,
                )
            }
        }
        if (datePicker != null) {
            val pickingStart = datePicker == R.string.transactions_date_start
            DateSelectionDialog(
                initial = if (pickingStart) start else end,
                onDismiss = { datePicker = null },
                onPick = { date ->
                    draft = if (pickingStart) draft.copy(customStart = date, customEnd = end)
                        else draft.copy(customStart = start, customEnd = date)
                },
                earliest = start.takeUnless { pickingStart },
                latest = end.takeIf { pickingStart },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterOptions(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) { content() }
}

@Composable
private fun AmountBoundField(
    value: String,
    onChange: (String) -> Unit,
    @StringRes label: Int,
    invalid: Boolean,
    reversed: Boolean = false,
) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = MaterialTheme.shapes.medium,
        isError = invalid || reversed,
        supportingText = if (invalid || reversed) {
            { Text(stringResource(if (invalid) R.string.transactions_amount_error else R.string.transactions_amount_order)) }
        } else null,
    )
}

@get:StringRes
internal val TransactionFilter.labelRes: Int
    get() = when (this) {
        TransactionFilter.ALL -> R.string.filter_all
        TransactionFilter.EXPENSE -> R.string.filter_expense
        TransactionFilter.INCOME -> R.string.filter_income
        TransactionFilter.TRANSFER -> R.string.add_transfer
    }

@get:StringRes
internal val TransactionsRange.labelRes: Int
    get() = when (this) {
        TransactionsRange.MONTH -> R.string.transactions_range_month
        TransactionsRange.LAST_THREE_MONTHS -> R.string.transactions_range_three_months
        TransactionsRange.YEAR -> R.string.transactions_range_year
        TransactionsRange.ALL_TIME -> R.string.transactions_range_all
        TransactionsRange.CUSTOM -> R.string.transactions_range_custom
    }

private fun Set<Long>.toggle(id: Long) = if (id in this) this - id else this + id
