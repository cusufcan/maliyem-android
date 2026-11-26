package com.mercan.maliyem.ui.accounts

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.AccountEntity
import com.mercan.maliyem.data.local.DefaultAccounts
import com.mercan.maliyem.ui.components.SettingRow
import com.mercan.maliyem.ui.components.SeyirChip
import com.mercan.maliyem.ui.theme.categoryIcon

/** Hazır hesabın adı, özel bir ad yazılmadıysa seçili dili izler. */
@Composable
fun accountName(account: AccountEntity): String =
    account.name ?: DefaultAccounts.nameRes(account.key)?.let { stringResource(it) }.orEmpty()

@Composable
fun accountName(accounts: List<AccountEntity>, id: Long): String =
    accounts.firstOrNull { it.id == id }?.let { accountName(it) }.orEmpty()

@Composable
fun AccountPicker(
    @StringRes label: Int,
    selected: AccountEntity?,
    choices: List<AccountEntity>,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val name = selected?.let { accountName(it) } ?: stringResource(R.string.account_choose)
    val icon = selected?.let { categoryIcon(it.icon) } ?: R.drawable.ic_seyir_wallet
    if (choices.isEmpty()) {
        SettingRow(icon, stringResource(label), value = name)
        return
    }
    Box {
        SeyirChip(
            label = stringResource(R.string.account_picker_label, stringResource(label), name),
            selected = false,
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            icon = icon,
            role = Role.Button,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (account in choices) {
                DropdownMenuItem(
                    text = { Text(accountName(account)) },
                    onClick = {
                        expanded = false
                        onSelect(account.id)
                    },
                )
            }
        }
    }
}
