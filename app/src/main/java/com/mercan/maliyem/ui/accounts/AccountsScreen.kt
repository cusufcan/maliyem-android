package com.mercan.maliyem.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.AccountEntity
import com.mercan.maliyem.data.local.DefaultAccounts
import com.mercan.maliyem.data.repository.AccountBalance
import com.mercan.maliyem.ui.add.amountInputText
import com.mercan.maliyem.ui.categories.CategoryIconPicker
import com.mercan.maliyem.ui.components.BackHeader
import com.mercan.maliyem.ui.components.CardLabel
import com.mercan.maliyem.ui.components.SettingRow
import com.mercan.maliyem.ui.components.SeyirButton
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SubText
import com.mercan.maliyem.ui.theme.Motion
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.ui.theme.categoryIcon
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
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            itemsIndexed(balances, key = { _, item -> item.account.id }) { _, item ->
                val account = item.account
                SeyirCard(
                    Modifier.animateItem(fadeInSpec = Motion.medium(), fadeOutSpec = Motion.fast()),
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
        SeyirButton(
            stringResource(R.string.account_new), { editing = 0 },
            Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
            icon = R.drawable.ic_seyir_plus, enabled = !busy,
        )
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
                edited.hasActivity && !account.archived -> R.string.account_archive_in_use
                else -> null
            },
            onArchive = account?.takeIf { canArchive }?.let {
                { viewModel.setArchived(it); editing = null }
            },
            onDelete = edited?.takeIf { canArchive && !it.hasActivity && it.account.key != DefaultAccounts.CASH }?.let {
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (account == null) R.string.account_new else R.string.account_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    name, { name = it.take(40) }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.categories_name)) }, singleLine = true,
                )
                OutlinedTextField(
                    balance, { balance = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.account_initial_balance)) },
                    singleLine = true,
                    isError = amount == null,
                    supportingText = if (amount == null) {
                        { Text(stringResource(R.string.account_balance_invalid)) }
                    } else null,
                )
                availabilityMessage?.let { SubText(stringResource(it)) }
                if (onArchive != null || onDelete != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        onArchive?.let {
                            TextButton(it) {
                                Text(stringResource(if (account?.archived == true) R.string.account_unarchive else R.string.account_archive))
                            }
                        }
                        onDelete?.let {
                            TextButton(it) { Text(stringResource(R.string.detail_delete), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                SubText(stringResource(R.string.categories_icon))
                CategoryIconPicker(icon) { icon = it }
            }
        },
        confirmButton = {
            TextButton(
                { onSave(savedName, icon, checkNotNull(amount)) },
                enabled = name.isNotBlank() && amount != null,
            ) { Text(stringResource(R.string.add_save)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.add_cancel)) } },
    )
}

/** Yalnız etkin hesapların tüm zamanlardaki bakiyeleri. */
@Composable
fun AccountsCard(accounts: List<AccountBalance>) {
    if (accounts.size < 2) return
    val locale = LocalConfiguration.current.locales[0]
    SeyirCard {
        CardLabel(stringResource(R.string.settings_accounts))
        for ((account, balance) in accounts) {
            SettingRow(categoryIcon(account.icon), accountName(account), value = formatMoney(balance, locale))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingRow(null, stringResource(R.string.account_total), value = formatMoney(accounts.sumOf { it.balanceMinor }, locale))
    }
}
