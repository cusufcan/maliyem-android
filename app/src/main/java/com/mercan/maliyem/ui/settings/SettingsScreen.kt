package com.mercan.maliyem.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mercan.maliyem.R
import com.mercan.maliyem.ui.components.CardLabel
import com.mercan.maliyem.ui.components.ScreenTitle
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SeyirSegmented
import com.mercan.maliyem.ui.components.SettingRow
import com.mercan.maliyem.ui.theme.Spacing
import java.time.LocalDate

@Composable
fun SettingsScreen(
    onCategories: () -> Unit,
    onAccounts: () -> Unit,
    onRecurring: () -> Unit,
    onBudgets: () -> Unit,
    snackbar: SnackbarHostState,
    beforeRestore: suspend () -> Unit,
    securityCard: @Composable () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val count by viewModel.categoryCount.collectAsStateWithLifecycle()
    val accountCount by viewModel.accountCount.collectAsStateWithLifecycle()
    val recurringCount by viewModel.recurringCount.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val dataBusy by viewModel.dataBusy.collectAsStateWithLifecycle()
    val pendingBackup by viewModel.pendingBackup.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::saveBackup)
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::openBackup)
    }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.exportCsv(it, resources) }
    }
    LaunchedEffect(viewModel, resources, snackbar) {
        viewModel.dataMessages.collect { snackbar.showSnackbar(resources.getString(it)) }
    }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    val divider = MaterialTheme.colorScheme.outlineVariant
    val rows = PaddingValues(horizontal = Spacing.lg)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ScreenTitle(stringResource(R.string.nav_settings))
        SeyirCard(padding = rows) {
            SettingRow(
                R.drawable.ic_seyir_tag, stringResource(R.string.settings_categories),
                value = count?.toString(), onClick = onCategories, horizontalPadding = 0.dp,
            )
            HorizontalDivider(color = divider)
            SettingRow(
                R.drawable.ic_seyir_wallet, stringResource(R.string.settings_accounts),
                value = accountCount?.toString(), onClick = onAccounts, horizontalPadding = 0.dp,
            )
            HorizontalDivider(color = divider)
            SettingRow(
                R.drawable.ic_seyir_repeat, stringResource(R.string.settings_recurring),
                value = recurringCount?.toString(), onClick = onRecurring, horizontalPadding = 0.dp,
            )
            HorizontalDivider(color = divider)
            SettingRow(
                R.drawable.ic_seyir_breakdown, stringResource(R.string.settings_budgets),
                onClick = onBudgets, horizontalPadding = 0.dp,
            )
            HorizontalDivider(color = divider)
            SettingRow(
                R.drawable.ic_seyir_wallet, stringResource(R.string.settings_currency),
                value = stringResource(R.string.settings_currency_value), horizontalPadding = 0.dp,
            )
        }
        SeyirCard {
            CardLabel(stringResource(R.string.settings_theme))
            SeyirSegmented(
                listOf(R.string.settings_system, R.string.settings_theme_light, R.string.settings_theme_dark).map { stringResource(it) },
                theme.ordinal,
                { viewModel.setTheme(ThemeMode.entries[it]) },
                Modifier.padding(top = 10.dp),
            )
        }
        SeyirCard {
            CardLabel(stringResource(R.string.settings_language))
            // Dil adları kendi dillerinde yazılır; böylece yanlış dile geçen kullanıcı yolunu bulur.
            SeyirSegmented(
                listOf(stringResource(R.string.settings_system), "English", "Türkçe"),
                language.ordinal,
                { viewModel.setLanguage(AppLanguage.entries[it]) },
                Modifier.padding(top = 10.dp),
            )
        }
        securityCard()
        SeyirCard(padding = rows) {
            SettingRow(
                R.drawable.ic_seyir_lock, stringResource(R.string.settings_data),
                subtitle = stringResource(R.string.settings_data_body), horizontalPadding = 0.dp,
            )
            HorizontalDivider(color = divider)
            SettingRow(
                R.drawable.ic_seyir_arrow_out, stringResource(R.string.settings_backup),
                onClick = { backupPicker.launch("maliyem-yedek-${LocalDate.now()}.json") },
                horizontalPadding = 0.dp, enabled = !dataBusy,
            )
            HorizontalDivider(color = divider)
            SettingRow(
                R.drawable.ic_seyir_arrow_in, stringResource(R.string.settings_restore),
                onClick = { restorePicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                horizontalPadding = 0.dp, enabled = !dataBusy,
            )
            HorizontalDivider(color = divider)
            SettingRow(
                R.drawable.ic_seyir_note, stringResource(R.string.settings_csv),
                onClick = { csvPicker.launch("maliyem-${LocalDate.now()}.csv") },
                horizontalPadding = 0.dp, enabled = !dataBusy,
            )
            HorizontalDivider(color = divider)
            SettingRow(null, stringResource(R.string.settings_version), value = version, horizontalPadding = 0.dp)
        }
    }
    pendingBackup?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissBackup,
            title = { Text(stringResource(R.string.settings_restore)) },
            text = {
                Text(stringResource(
                    R.string.settings_restore_body,
                    pending.backup.transactions.size, pending.categoryCount,
                    pending.backup.accounts.size, pending.backup.transfers.size, pending.backup.recurringRules.size,
                ))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.restoreBackup(beforeRestore) }, enabled = !dataBusy) {
                    Text(stringResource(if (dataBusy) R.string.settings_restoring else R.string.settings_restore_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissBackup, enabled = !dataBusy) { Text(stringResource(R.string.add_cancel)) }
            },
        )
    }
}
