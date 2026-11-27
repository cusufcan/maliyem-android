package com.mercan.maliyem.ui.csvimport

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mercan.maliyem.R
import com.mercan.maliyem.data.csv.CsvField
import com.mercan.maliyem.data.csv.CsvFileError
import com.mercan.maliyem.data.csv.CsvRowError
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.ui.components.BackHeader
import com.mercan.maliyem.ui.components.CardLabel
import com.mercan.maliyem.ui.components.ScreenLoading
import com.mercan.maliyem.ui.components.SettingRow
import com.mercan.maliyem.ui.components.SeyirActionButton
import com.mercan.maliyem.ui.components.SeyirButton
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SeyirFormSheet
import com.mercan.maliyem.ui.components.SubText
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.ui.theme.seyirAnimateItem

@Composable
internal fun CsvImportScreen(onExit: () -> Unit, viewModel: CsvImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val writing = (state as? CsvImportState.Preview)?.writing == true
    BackHandler(enabled = writing) { }
    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.lg)) {
        val title = when (state) {
            is CsvImportState.Mapping -> R.string.csv_map_title
            is CsvImportState.Preview -> R.string.csv_preview
            else -> R.string.csv_import
        }
        BackHeader(stringResource(title), onBack = { if (!writing) onExit() }, enabled = !writing)
        when (val current = state) {
            CsvImportState.Loading -> ScreenLoading()
            is CsvImportState.Failed -> SeyirCard { CsvError(stringResource(current.reason.label)) }
            is CsvImportState.Mapping -> MappingContent(
                current, viewModel::setHasHeader, viewModel::selectColumn, viewModel::previewMapping,
            )
            is CsvImportState.Preview -> PreviewContent(
                current, viewModel::editMapping, viewModel::setSkipDuplicates, viewModel::confirm,
            )
            is CsvImportState.Finished -> Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                SeyirCard {
                    CardLabel(stringResource(R.string.csv_finished))
                    SubText(stringResource(
                        R.string.csv_finished_counts,
                        current.result.written, current.result.skippedDuplicates, current.result.errors,
                    ))
                }
                SeyirButton(stringResource(R.string.csv_done), onExit, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MappingContent(
    state: CsvImportState.Mapping,
    onHeader: (Boolean) -> Unit,
    onSelect: (CsvField, Int?) -> Unit,
    onPreview: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf<CsvField?>(null) }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SubText(stringResource(R.string.csv_map_hint))
            SeyirCard {
                CsvSwitch(stringResource(R.string.csv_header), state.mapping.hasHeader, onHeader)
            }
            SeyirCard {
                for (field in CsvField.entries) {
                    val selected = state.mapping.columns[field]
                    SettingRow(
                        icon = null,
                        label = stringResource(field.label),
                        subtitle = selected?.let { columnLabel(state, it) } ?: stringResource(field.unmappedLabel),
                        onClick = { picking = field },
                        horizontalPadding = 0.dp,
                    )
                }
            }
            if (state.tooManyRows) CsvError(stringResource(R.string.csv_row_limit))
        }
        SeyirButton(
            stringResource(R.string.csv_preview), onPreview,
            Modifier.fillMaxWidth().padding(bottom = Spacing.lg),
            enabled = !state.tooManyRows && state.mapping.isValid(state.width),
        )
    }

    picking?.let { field ->
        SeyirFormSheet(
            title = stringResource(field.label),
            onDismiss = { picking = null },
            dismissText = stringResource(R.string.add_cancel),
            scrollContent = false,
        ) {
            LazyColumn {
                item(key = "unmapped") {
                    Column(seyirAnimateItem()) {
                        SettingRow(null, stringResource(field.unmappedLabel), onClick = {
                            onSelect(field, null)
                            picking = null
                        }, horizontalPadding = 0.dp)
                    }
                }
                items(state.width, key = { it }) { index ->
                    Column(seyirAnimateItem()) {
                        val selected = state.mapping.columns[field] == index
                        SettingRow(
                            null, columnLabel(state, index),
                            subtitle = state.document.records.getOrNull(if (state.mapping.hasHeader) 1 else 0)
                                ?.cells?.getOrNull(index)?.take(100)?.takeIf(String::isNotBlank),
                            value = if (selected) stringResource(R.string.csv_selected) else null,
                            onClick = {
                                onSelect(field, index)
                                picking = null
                            },
                            horizontalPadding = 0.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun columnLabel(state: CsvImportState.Mapping, index: Int): String {
    val title = state.document.columnTitle(index, state.mapping.hasHeader)?.take(100)
    return if (title == null) stringResource(R.string.csv_column, index + 1)
    else stringResource(R.string.csv_named_column, index + 1, title)
}

@Composable
private fun PreviewContent(
    state: CsvImportState.Preview,
    onEdit: () -> Unit,
    onDuplicate: (Boolean) -> Unit,
    onConfirm: () -> Unit,
) {
    val preview = state.data
    val busy = state.writing || state.updating
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "preview-controls") {
                SeyirCard(seyirAnimateItem()) {
                    CardLabel(stringResource(R.string.csv_preview_counts, preview.total, preview.ready))
                    SubText(stringResource(R.string.csv_preview_skipped, preview.issues.size, preview.duplicates))
                    CsvSwitch(stringResource(R.string.csv_skip_duplicates), state.skipDuplicates, onDuplicate, !busy)
                    SeyirActionButton(
                        stringResource(R.string.csv_edit_mapping), onEdit,
                        icon = R.drawable.ic_seyir_edit, enabled = !busy,
                    )
                    if (state.failed) CsvError(stringResource(R.string.csv_action_failed))
                }
            }
            if (preview.newCategories.isNotEmpty()) {
                item(key = "new_categories") {
                    Column(seyirAnimateItem()) { CardLabel(stringResource(R.string.csv_new_categories, preview.newCategories.size)) }
                }
                items(preview.newCategories, key = { "category-${it.type}-${it.name}" }) { category ->
                    val type = stringResource(if (category.type == TransactionType.EXPENSE) R.string.add_expense else R.string.add_income)
                    SeyirCard(seyirAnimateItem()) { Text(stringResource(R.string.csv_category_preview, category.name.take(100), type)) }
                }
            }
            if (preview.newAccounts.isNotEmpty()) {
                item(key = "new_accounts") {
                    Column(seyirAnimateItem()) { CardLabel(stringResource(R.string.csv_new_accounts, preview.newAccounts.size)) }
                }
                items(preview.newAccounts, key = { "account-$it" }) { account ->
                    SeyirCard(seyirAnimateItem()) { Text(account.take(100)) }
                }
            }
            if (preview.issues.isNotEmpty()) {
                item(key = "errors") {
                    Column(seyirAnimateItem()) { CardLabel(stringResource(R.string.csv_errors)) }
                }
                items(preview.issues, key = { it.line }) { issue ->
                    SeyirCard(seyirAnimateItem()) {
                        CardLabel(stringResource(R.string.csv_error_row, issue.line))
                        CsvError(stringResource(issue.reason.label))
                    }
                }
            }
        }
        val button = when {
            state.writing -> R.string.csv_writing
            state.updating -> R.string.csv_updating
            else -> R.string.csv_confirm
        }
        SeyirButton(
            stringResource(button), onConfirm, Modifier.fillMaxWidth().padding(bottom = Spacing.lg),
            enabled = !busy && preview.ready > 0,
        )
    }
}

@Composable
private fun CsvSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun CsvError(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

private val CsvField.label: Int
    @StringRes get() = when (this) {
        CsvField.DATE -> R.string.detail_date
        CsvField.AMOUNT -> R.string.add_amount
        CsvField.TYPE -> R.string.csv_type
        CsvField.CATEGORY -> R.string.detail_category
        CsvField.NOTE -> R.string.add_note
        CsvField.ACCOUNT -> R.string.account_label
    }

private val CsvField.unmappedLabel: Int
    @StringRes get() = when (this) {
        CsvField.DATE, CsvField.AMOUNT -> R.string.csv_select_column
        CsvField.TYPE -> R.string.csv_amount_sign
        CsvField.CATEGORY -> R.string.csv_default_category
        CsvField.NOTE -> R.string.csv_ignore
        CsvField.ACCOUNT -> R.string.account_cash
    }

private val CsvFileError.label: Int
    @StringRes get() = when (this) {
        CsvFileError.SIZE_LIMIT -> R.string.csv_size_limit
        CsvFileError.ROW_LIMIT -> R.string.csv_row_limit
        CsvFileError.EMPTY -> R.string.csv_empty
        CsvFileError.UNREADABLE -> R.string.csv_unreadable
    }

private val CsvRowError.label: Int
    @StringRes get() = when (this) {
        CsvRowError.QUOTING -> R.string.csv_invalid_quotes
        CsvRowError.COLUMN_COUNT -> R.string.csv_invalid_columns
        CsvRowError.DATE -> R.string.csv_invalid_date
        CsvRowError.AMOUNT -> R.string.csv_invalid_amount
        CsvRowError.TYPE -> R.string.csv_invalid_type
        CsvRowError.TRANSFER_ACCOUNTS -> R.string.csv_invalid_transfer
    }
