package com.mercan.maliyem.ui.recurring

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.AccountEntity
import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.RecurringFrequency
import com.mercan.maliyem.data.local.RecurringRuleEntity
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.repository.forType
import com.mercan.maliyem.ui.accounts.AccountPicker
import com.mercan.maliyem.ui.add.amountInputText
import com.mercan.maliyem.ui.add.parseAmount
import com.mercan.maliyem.ui.add.pickAccount
import com.mercan.maliyem.ui.add.pickCategory
import com.mercan.maliyem.ui.components.ActionTone
import com.mercan.maliyem.ui.components.BackHeader
import com.mercan.maliyem.ui.components.DateSelectionDialog
import com.mercan.maliyem.ui.components.EmptyState
import com.mercan.maliyem.ui.components.ScreenLoading
import com.mercan.maliyem.ui.components.SettingRow
import com.mercan.maliyem.ui.components.SeyirActionButton
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SeyirChip
import com.mercan.maliyem.ui.components.SeyirFabClearance
import com.mercan.maliyem.ui.components.SeyirFormSheet
import com.mercan.maliyem.ui.components.SeyirListScaffold
import com.mercan.maliyem.ui.components.SubText
import com.mercan.maliyem.ui.components.categoryName
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.ui.theme.seyirAnimateItem
import com.mercan.maliyem.util.formatMoney
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun RecurringScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    viewModel: RecurringViewModel = hiltViewModel(),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
    val listState = rememberLazyListState()
    SeyirListScaffold(stringResource(R.string.recurring_new), listState, onAdd) {
        Column(
            Modifier.fillMaxSize().padding(top = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(Modifier.padding(horizontal = Spacing.lg)) {
                BackHeader(stringResource(R.string.settings_recurring), onBack)
            }
            val rows = rules
            if (rows == null) {
                ScreenLoading()
                return@Column
            }
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = SeyirFabClearance),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(rows, key = { it.id }) { rule ->
                        val category = categories.firstOrNull { it.id == rule.categoryId }
                        val categoryText = category?.let { categoryName(it.key, it.name) }.orEmpty()
                        val title = listOfNotNull(categoryText, rule.note).filter { it.isNotBlank() }.joinToString(" · ")
                        val next = if (rule.hasEnded()) null
                            else stringResource(R.string.recurring_next_date, rule.nextDate.format(formatter))
                        val status = ruleStatus(rule)
                        SeyirCard(
                            seyirAnimateItem(),
                            padding = PaddingValues(),
                        ) {
                            SettingRow(
                                R.drawable.ic_seyir_repeat, title,
                                subtitle = listOfNotNull(stringResource(rule.frequency.labelRes()), next, status).joinToString(" · "),
                                value = formatMoney(
                                    if (rule.type == TransactionType.INCOME) rule.amountMinor else -rule.amountMinor,
                                    locale, signed = true,
                                ),
                                onClick = { onOpen(rule.id) },
                            )
                        }
                    }
                }
                if (rows.isEmpty()) {
                    EmptyState(
                        R.drawable.ic_seyir_repeat, stringResource(R.string.recurring_empty_title),
                        stringResource(R.string.recurring_empty),
                        Modifier.fillMaxSize().padding(start = Spacing.lg, end = Spacing.lg, bottom = SeyirFabClearance),
                    )
                }
            }
        }
    }

}

@Composable
fun RecurringRuleScreen(id: Long, onBack: () -> Unit, viewModel: RecurringViewModel = hiltViewModel()) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val writeFailed by viewModel.writeFailed.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    LaunchedEffect(finished) { if (finished) onBack() }
    BackHandler(enabled = busy) {}

    Column(
        Modifier.fillMaxSize()
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        BackHeader(stringResource(R.string.recurring_edit), onBack = { if (!busy) onBack() })
        val rows = rules ?: return@Column
        val rule = rows.firstOrNull { it.id == id }
        if (rule == null) {
            if (!finished) SubText(stringResource(R.string.recurring_missing))
            return@Column
        }
        RuleForm(
            rule, categories.forType(rule.type), accounts, busy, writeFailed,
            onSave = { amount, category, account, note, frequency, end ->
                viewModel.save(id, amount, category, account, note, frequency, end)
            },
            onPause = { viewModel.setPaused(id, !rule.paused) },
            onDelete = { viewModel.delete(id) },
            onDismiss = onBack,
        )
    }
}

private fun RecurringRuleEntity.hasEnded() = nextDate == LocalDate.MAX || (endDate != null && nextDate > endDate)

@Composable
private fun ruleStatus(rule: RecurringRuleEntity): String = stringResource(when {
    rule.paused -> R.string.recurring_paused
    rule.hasEnded() -> R.string.recurring_ended
    else -> R.string.recurring_active
})

@Composable
private fun RuleForm(
    rule: RecurringRuleEntity,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    busy: Boolean,
    writeFailed: Boolean,
    onSave: (Long, Long, Long, String, RecurringFrequency, LocalDate?) -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    var amount by rememberSaveable(rule.id, locale) { mutableStateOf(amountInputText(rule.amountMinor, locale)) }
    var category by rememberSaveable(rule.id) { mutableStateOf<Long?>(null) }
    var account by rememberSaveable(rule.id) { mutableStateOf<Long?>(null) }
    var note by rememberSaveable(rule.id) { mutableStateOf(rule.note.orEmpty()) }
    var frequency by rememberSaveable(rule.id) { mutableStateOf(rule.frequency.name) }
    var end by rememberSaveable(rule.id) { mutableStateOf(rule.endDate?.toString()) }
    var calendar by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val minor = parseAmount(amount, locale)?.takeIf { it > 0 }
    val categoryId = pickCategory(category ?: rule.categoryId, categories)
    val accountId = pickAccount(account ?: rule.accountId, rule.accountId, accounts)
    val selectedFrequency = RecurringFrequency.valueOf(frequency)
    val endDate = end?.let(LocalDate::parse)
    val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
    val confirmEnabled = !busy && minor != null && categoryId != null && accountId != null
    val confirm = {
        onSave(checkNotNull(minor), checkNotNull(categoryId), checkNotNull(accountId), note, selectedFrequency, endDate)
    }
    SeyirFormSheet(
        title = stringResource(R.string.recurring_edit),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.add_save),
        onConfirm = confirm,
        confirmEnabled = confirmEnabled,
        dismissText = stringResource(R.string.add_cancel),
        dismissEnabled = !busy,
    ) {
        OutlinedTextField(
            amount, { amount = it }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.add_amount)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true, enabled = !busy, isError = minor == null,
        )
        SubText(stringResource(R.string.detail_category))
        FlowRow(
            Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            for (item in categories) {
                SeyirChip(categoryName(item.key, item.name), item.id == categoryId, { category = item.id }, enabled = !busy)
            }
        }
        AccountPicker(
            R.string.account_label, accounts.firstOrNull { it.id == accountId },
            accounts.filter { !busy && (!it.archived || it.id == rule.accountId) },
            { account = it },
        )
        OutlinedTextField(
            note, { note = it }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.add_note)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (confirmEnabled) confirm() }),
            singleLine = true, enabled = !busy,
        )
        FrequencyPicker(selectedFrequency, { frequency = checkNotNull(it).name }, enabled = !busy)
        SettingRow(
            R.drawable.ic_seyir_calendar, stringResource(R.string.recurring_end_date),
            value = endDate?.format(formatter) ?: stringResource(R.string.recurring_no_end),
            modifier = Modifier.clip(MaterialTheme.shapes.large),
            onClick = { calendar = true }, horizontalPadding = 0.dp, enabled = !busy,
        )
        if (endDate != null) {
            SeyirActionButton(
                stringResource(R.string.recurring_clear_end), { end = null },
                icon = R.drawable.ic_seyir_close, enabled = !busy,
            )
        }
        if (writeFailed) {
            Text(
                stringResource(R.string.add_error_write),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        SubText(ruleStatus(rule))
        SubText(stringResource(R.string.recurring_pause_note))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SeyirActionButton(
                stringResource(if (rule.paused) R.string.recurring_resume else R.string.recurring_pause),
                onPause, icon = if (rule.paused) R.drawable.ic_seyir_play else R.drawable.ic_seyir_pause, enabled = !busy,
            )
            SeyirActionButton(
                stringResource(R.string.detail_delete), { deleting = true },
                icon = R.drawable.ic_seyir_trash, tone = ActionTone.Danger, enabled = !busy,
            )
        }
    }

    if (calendar) {
        DateSelectionDialog(
            initial = endDate ?: rule.nextDate.takeUnless { it == LocalDate.MAX } ?: rule.startDate,
            earliest = rule.startDate,
            onDismiss = { calendar = false },
            onPick = { end = it.toString() },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.recurring_delete_title)) },
            text = { Text(stringResource(R.string.recurring_delete_body)) },
            confirmButton = {
                TextButton({ deleting = false; onDelete() }, enabled = !busy) { Text(stringResource(R.string.detail_delete)) }
            },
            dismissButton = {
                TextButton({ deleting = false }) { Text(stringResource(R.string.add_cancel)) }
            },
        )
    }
}
