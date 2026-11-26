package com.mercan.maliyem.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mercan.maliyem.R
import com.mercan.maliyem.ui.add.amountInputText
import com.mercan.maliyem.ui.add.parseAmount
import com.mercan.maliyem.ui.components.BackHeader
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SettingRow
import com.mercan.maliyem.ui.components.categoryName
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.ui.theme.categoryIcon
import com.mercan.maliyem.util.formatMoney

@Composable
fun BudgetsScreen(
    onBack: () -> Unit,
    onBudgetCreated: () -> Unit,
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val budgetCreated by viewModel.budgetCreated.collectAsStateWithLifecycle()
    LaunchedEffect(budgetCreated) {
        if (budgetCreated) {
            viewModel.acknowledgeBudgetCreated()
            onBudgetCreated()
        }
    }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val failed by viewModel.writeFailed.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val overallTitle = stringResource(R.string.budget_overall)
    val noLimit = stringResource(R.string.budget_no_limit)
    val current = settings

    Column(Modifier.fillMaxSize().padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Column(Modifier.padding(horizontal = Spacing.lg)) {
            BackHeader(stringResource(R.string.settings_budgets), onBack)
        }
        if (current != null) {
            LazyColumn(
                contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                item {
                    SeyirCard(padding = PaddingValues()) {
                        SettingRow(
                            R.drawable.ic_seyir_wallet, overallTitle,
                            value = current.overallLimit?.let { formatMoney(it, locale) } ?: noLimit,
                            onClick = { viewModel.edit(OVERALL_BUDGET) },
                        )
                    }
                }
                items(current.categories, key = { it.id }) { category ->
                    SeyirCard(padding = PaddingValues()) {
                        SettingRow(
                            categoryIcon(category.icon), categoryName(category.key, category.name),
                            value = current.limits[category.id]?.let { formatMoney(it, locale) } ?: noLimit,
                            onClick = { viewModel.edit(category.id) },
                        )
                    }
                }
            }
        }
    }

    if (current != null) editing?.let { id ->
        val overall = id == OVERALL_BUDGET
        val category = current.categories.firstOrNull { it.id == id }
        if (overall || category != null) key(id) {
            BudgetDialog(
                title = category?.let { categoryName(it.key, it.name) } ?: overallTitle,
                limit = if (overall) current.overallLimit else current.limits[id],
                busy = busy,
                failed = failed,
                onSave = viewModel::save,
                onDismiss = { viewModel.edit(null) },
            )
        }
    }
}

@Composable
private fun BudgetDialog(
    title: String,
    limit: Long?,
    busy: Boolean,
    failed: Boolean,
    onSave: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    var text by rememberSaveable { mutableStateOf(limit?.let { amountInputText(it, locale) }.orEmpty()) }
    val amount = parseAmount(text, locale)?.takeIf { it > 0 }
    val invalid = text.isNotBlank() && amount == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    text, { text = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.budget_limit)) },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = invalid,
                    supportingText = if (invalid) { { Text(stringResource(R.string.budget_invalid_limit)) } } else null,
                )
                if (failed) {
                    Text(
                        stringResource(R.string.budget_write_failed),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (limit != null) {
                    TextButton(onClick = { onSave(null) }, enabled = !busy) {
                        Text(stringResource(R.string.budget_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(checkNotNull(amount)) }, enabled = !busy && amount != null && amount != limit) {
                Text(stringResource(R.string.add_save))
            }
        },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text(stringResource(R.string.add_cancel)) } },
    )
}
