package com.mercan.maliyem.ui.accounts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.AccountEntity
import com.mercan.maliyem.data.local.DefaultAccounts
import com.mercan.maliyem.data.repository.AccountBalance
import com.mercan.maliyem.ui.add.amountInputText
import com.mercan.maliyem.ui.categories.CategoryIconPicker
import com.mercan.maliyem.ui.components.ActionTone
import com.mercan.maliyem.ui.components.BackHeader
import com.mercan.maliyem.ui.components.CardLabel
import com.mercan.maliyem.ui.components.EmptyState
import com.mercan.maliyem.ui.components.SettingRow
import com.mercan.maliyem.ui.components.SeyirActionButton
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SeyirFabClearance
import com.mercan.maliyem.ui.components.SeyirFormSheet
import com.mercan.maliyem.ui.components.SeyirListScaffold
import com.mercan.maliyem.ui.components.SubText
import com.mercan.maliyem.ui.theme.Motion
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.ui.theme.categoryIcon
import com.mercan.maliyem.ui.theme.seyirAnimateItem
import com.mercan.maliyem.util.formatMoney
import com.mercan.maliyem.util.parseMoney

@Composable
fun AccountsScreen(onBack: () -> Unit, viewModel: AccountsViewModel = hiltViewModel()) {
    val balances by viewModel.balances.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val writeFailed by viewModel.writeFailed.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }

    val listState = rememberLazyListState()
    SeyirListScaffold(
        addLabel = if (busy) null else stringResource(R.string.account_new),
        listState = listState,
        onAdd = { editing = 0L },
    ) {
        Column(
            Modifier.fillMaxSize().padding(top = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(Modifier.padding(horizontal = Spacing.lg)) {
                BackHeader(stringResource(R.string.settings_accounts), onBack)
            }
            if (writeFailed) {
                Text(
                    stringResource(R.string.account_write_failed),
                    Modifier.padding(horizontal = Spacing.lg).semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = SeyirFabClearance),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(balances, key = { it.account.id }) { item ->
                        val account = item.account
                        SeyirCard(
                            seyirAnimateItem(),
                            padding = PaddingValues(),
                        ) {
                            SettingRow(
                                categoryIcon(account.icon), accountName(account),
                                value = formatMoney(item.balanceMinor, locale),
                                subtitle = when {
                                    account.archived -> stringResource(R.string.account_archived)
                                    account.key == DefaultAccounts.CASH -> stringResource(R.string.account_default)
                                    else -> null
                                },
                                onClick = { editing = account.id },
                                enabled = !busy,
                            )
                        }
                    }
                }
                if (balances.isEmpty()) {
                    EmptyState(
                        R.drawable.ic_seyir_wallet, stringResource(R.string.accounts_empty_title),
                        stringResource(R.string.accounts_empty_body),
                        Modifier.fillMaxSize().padding(start = Spacing.lg, end = Spacing.lg, bottom = SeyirFabClearance),
                    )
                }
            }
        }
    }

    val edited = balances.firstOrNull { it.account.id == editing }
    if (editing == 0L || edited != null) {
        val account = edited?.account
        val canArchive = account?.archived == true || balances.count { !it.account.archived } > 1
        AccountDialog(
            account = account,
            onDismiss = { editing = null },
            onSave = { name, icon, balance ->
                viewModel.save(account, name, icon, balance)
                editing = null
            },
            availabilityMessage = when {
                account == null -> null
                !canArchive -> R.string.account_active_required
                edited.hasRecurringRules -> R.string.account_recurring_in_use
                edited.hasActivity && !account.archived -> R.string.account_archive_in_use
                else -> null
            },
            onArchive = account?.takeIf { canArchive }?.let {
                { viewModel.setArchived(it); editing = null }
            },
            onDelete = edited?.takeIf { canArchive && !it.hasActivity && !it.hasRecurringRules && it.account.key != DefaultAccounts.CASH }?.let {
                { deleting = it.account.id; editing = null }
            },
        )
    }
    balances.firstOrNull { it.account.id == deleting }?.account?.let { account ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.account_delete_title, accountName(account))) },
            confirmButton = {
                TextButton({ viewModel.delete(account); deleting = null }, enabled = !busy) {
                    Text(stringResource(R.string.detail_delete))
                }
            },
            dismissButton = {
                TextButton({ deleting = null }) { Text(stringResource(R.string.add_cancel)) }
            },
        )
    }
}

@Composable
private fun AccountDialog(
    account: AccountEntity?,
    onDismiss: () -> Unit,
    onSave: (String?, String, Long) -> Unit,
    availabilityMessage: Int?,
    onArchive: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val locale = LocalConfiguration.current.locales[0]
    val initialName = account?.let { accountName(it) }.orEmpty()
    var name by rememberSaveable { mutableStateOf(initialName) }
    var icon by rememberSaveable { mutableStateOf(account?.icon ?: "wallet") }
    var balance by rememberSaveable { mutableStateOf(amountInputText(account?.initialBalanceMinor ?: 0, locale)) }
    val amount = if (balance.isBlank()) 0L else parseMoney(balance, locale, alternateDecimal = true, allowNegative = true)
    val savedName = if (name.trim() == initialName && account?.key == DefaultAccounts.CASH) account.name else name.trim()
    val confirmEnabled = name.isNotBlank() && amount != null
    val confirm = { onSave(savedName, icon, checkNotNull(amount)) }
    SeyirFormSheet(
        title = stringResource(if (account == null) R.string.account_new else R.string.account_edit),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.add_save),
        onConfirm = confirm,
        confirmEnabled = confirmEnabled,
        dismissText = stringResource(R.string.add_cancel),
    ) {
        OutlinedTextField(
            name, { name = it.take(40) }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.categories_name)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            balance, { balance = it }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.account_initial_balance)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (confirmEnabled) confirm() }),
            isError = amount == null,
            supportingText = if (amount == null) {
                { Text(stringResource(R.string.account_balance_invalid)) }
            } else null,
        )
        availabilityMessage?.let { SubText(stringResource(it)) }
        if (onArchive != null || onDelete != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                onArchive?.let {
                    val archived = account?.archived == true
                    SeyirActionButton(
                        stringResource(if (archived) R.string.account_unarchive else R.string.account_archive),
                        it, icon = if (archived) R.drawable.ic_seyir_arrow_out else R.drawable.ic_seyir_archive,
                    )
                }
                onDelete?.let {
                    SeyirActionButton(
                        stringResource(R.string.detail_delete), it,
                        icon = R.drawable.ic_seyir_trash, tone = ActionTone.Danger,
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SubText(stringResource(R.string.categories_icon))
            CategoryIconPicker(icon) { icon = it }
        }
    }
}

/** Yalnız etkin hesapların tüm zamanlardaki bakiyeleri. */
@Composable
fun AccountsCard(accounts: List<AccountBalance>) {
    val locale = LocalConfiguration.current.locales[0]
    AnimatedVisibility(accounts.size >= 2, enter = Motion.expand(), exit = Motion.collapse()) {
        SeyirCard(Modifier.animateContentSize(Motion.contentSize())) {
            CardLabel(stringResource(R.string.settings_accounts))
            for ((account, balance) in accounts) {
                key(account.id) {
                    SettingRow(categoryIcon(account.icon), accountName(account), value = formatMoney(balance, locale))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingRow(null, stringResource(R.string.account_total), value = formatMoney(accounts.sumOf { it.balanceMinor }, locale))
        }
    }
}
