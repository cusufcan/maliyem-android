package com.mercan.maliyem.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mercan.maliyem.R
import com.mercan.maliyem.ui.add.amountInputText
import com.mercan.maliyem.ui.add.parseAmount
import com.mercan.maliyem.ui.components.ActionTone
import com.mercan.maliyem.ui.components.BackHeader
import com.mercan.maliyem.ui.components.EmptyState
import com.mercan.maliyem.ui.components.ScreenLoading
import com.mercan.maliyem.ui.components.SettingRow
import com.mercan.maliyem.ui.components.SeyirActionButton
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SeyirFabClearance
import com.mercan.maliyem.ui.components.SeyirFormSheet
import com.mercan.maliyem.ui.components.SeyirListScaffold
import com.mercan.maliyem.ui.components.categoryName
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.ui.theme.categoryIcon
import com.mercan.maliyem.ui.theme.seyirAnimateItem
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
    val current = settings

    val listState = rememberLazyListState()
    var adding by rememberSaveable { mutableStateOf(false) }
    val availableCategories = current?.categories.orEmpty().filter { it.id !in current?.limits.orEmpty() }
    val canAddOverall = current != null && current.overallLimit == null
    val canAdd = canAddOverall || availableCategories.isNotEmpty()

    SeyirListScaffold(
        addLabel = if (canAdd) stringResource(R.string.budget_new) else null,
        listState = listState,
        onAdd = { if (!busy) adding = true },
    ) {
        Column(Modifier.fillMaxSize().padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Column(Modifier.padding(horizontal = Spacing.lg)) {
                BackHeader(stringResource(R.string.settings_budgets), onBack)
            }
            if (current == null) {
                ScreenLoading()
            } else {
                val configured = current.categories.filter { it.id in current.limits }
                val overallLimit = current.overallLimit
                Box(Modifier.weight(1f)) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = SeyirFabClearance),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        if (overallLimit != null) item(key = OVERALL_BUDGET) {
                            SeyirCard(seyirAnimateItem(), padding = PaddingValues()) {
                                SettingRow(
                                    R.drawable.ic_seyir_wallet, overallTitle, value = formatMoney(overallLimit, locale),
                                    onClick = { viewModel.edit(OVERALL_BUDGET) },
                                )
                            }
                        }
                        items(configured, key = { it.id }) { category ->
                            SeyirCard(seyirAnimateItem(), padding = PaddingValues()) {
                                SettingRow(
                                    categoryIcon(category.icon), categoryName(category.key, category.name),
                                    value = formatMoney(current.limits.getValue(category.id), locale),
                                    onClick = { viewModel.edit(category.id) },
                                )
                            }
                        }
                    }
                    if (overallLimit == null && configured.isEmpty()) {
                        EmptyState(
                            R.drawable.ic_seyir_breakdown, stringResource(R.string.budgets_empty_title),
                            stringResource(R.string.budgets_empty_body),
                            Modifier.fillMaxSize().padding(start = Spacing.lg, end = Spacing.lg, bottom = SeyirFabClearance),
                        )
                    }
                }
            }
        }
    }
    if (adding && canAdd) {
        fun pick(id: Long) {
            adding = false
            viewModel.edit(id)
        }
        SeyirFormSheet(
            title = stringResource(R.string.budget_new),
            onDismiss = { adding = false },
            dismissText = stringResource(R.string.add_cancel),
        ) {
            Column {
                if (canAddOverall) {
                    SettingRow(R.drawable.ic_seyir_wallet, overallTitle, onClick = { pick(OVERALL_BUDGET) })
                }
                availableCategories.forEach { category ->
                    key(category.id) {
                        SettingRow(
                            categoryIcon(category.icon), categoryName(category.key, category.name),
                            onClick = { pick(category.id) },
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
    val confirmEnabled = !busy && amount != null && amount != limit
    val confirm = { onSave(checkNotNull(amount)) }
    SeyirFormSheet(
        title = title,
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.add_save),
        onConfirm = confirm,
        confirmEnabled = confirmEnabled,
        dismissText = stringResource(R.string.add_cancel),
        dismissEnabled = !busy,
    ) {
        OutlinedTextField(
            text, { text = it }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.budget_limit)) },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (confirmEnabled) confirm() }),
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SeyirActionButton(
                    stringResource(R.string.budget_remove), { onSave(null) },
                    icon = R.drawable.ic_seyir_trash, tone = ActionTone.Danger, enabled = !busy,
                )
            }
        }
    }
}
