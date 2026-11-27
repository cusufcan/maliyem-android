package com.mercan.maliyem.ui.settings

import android.content.ActivityNotFoundException
import android.net.Uri
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mercan.maliyem.R
import com.mercan.maliyem.ui.components.CardLabel
import com.mercan.maliyem.ui.components.ScreenTitle
import com.mercan.maliyem.ui.components.SettingRow
import com.mercan.maliyem.ui.components.SettingSwitch
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SeyirSegmented
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.util.formatMoney
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onCategories: () -> Unit,
    onAccounts: () -> Unit,
    onRecurring: () -> Unit,
    onBudgets: () -> Unit,
    onGoals: () -> Unit,
    onOpenYearlyReport: () -> Unit,
    onImportCsv: (Uri) -> Unit,
    snackbar: SnackbarHostState,
    restoreWithUndoCleared: suspend (suspend () -> Unit) -> Unit,
    securityCard: @Composable () -> Unit,
    notificationsCard: @Composable () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val count by viewModel.categoryCount.collectAsStateWithLifecycle()
    val accountCount by viewModel.accountCount.collectAsStateWithLifecycle()
    val recurringCount by viewModel.recurringCount.collectAsStateWithLifecycle()
    val autoReadReceipts by viewModel.autoReadReceipts.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val dataBusy by viewModel.dataBusy.collectAsStateWithLifecycle()
    val pendingBackup by viewModel.pendingBackup.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::saveBackup)
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::openBackup)
    }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.exportCsv(it, resources) }
    }
    val csvImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImportCsv)
    }
    LaunchedEffect(viewModel, resources, snackbar) {
        viewModel.dataMessages.collect { snackbar.showSnackbar(resources.getString(it)) }
    }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    val divider = MaterialTheme.colorScheme.outlineVariant
    val rows = PaddingValues()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ScreenTitle(stringResource(R.string.nav_settings))
        SeyirCard(padding = rows) {
            SettingRow(
                R.drawable.ic_seyir_tag, stringResource(R.string.settings_categories),
                value = count?.toString(), onClick = onCategories,
            )
            HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = divider)
            SettingRow(
                R.drawable.ic_seyir_wallet, stringResource(R.string.settings_accounts),
                value = accountCount?.toString(), onClick = onAccounts,
            )
            HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = divider)
            SettingRow(
                R.drawable.ic_seyir_repeat, stringResource(R.string.settings_recurring),
                value = recurringCount?.toString(), onClick = onRecurring,
            )
            HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = divider)
            SettingRow(
                R.drawable.ic_seyir_breakdown, stringResource(R.string.settings_budgets),
                onClick = onBudgets,
            )
            HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = divider)
            SettingRow(
                R.drawable.ic_seyir_cat_piggy, stringResource(R.string.goals_title),
                onClick = onGoals,
            )
            HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = divider)
            SettingRow(
                R.drawable.ic_seyir_cat_percent, stringResource(R.string.settings_currency),
                value = stringResource(R.string.settings_currency_value),
            )
        }
        SeyirCard(padding = rows) {
            Column(Modifier.padding(start = Spacing.lg, top = Spacing.lg)) {
                CardLabel(stringResource(R.string.settings_reports))
            }
            SettingRow(R.drawable.ic_seyir_cat_chart, stringResource(R.string.yearly_report), onClick = onOpenYearlyReport)
        }
        SeyirCard {
            CardLabel(stringResource(R.string.settings_theme))
            SeyirSegmented(
                listOf(R.string.settings_system, R.string.settings_theme_light, R.string.settings_theme_dark).map { stringResource(it) },
                theme.ordinal,
                { viewModel.setTheme(ThemeMode.entries[it]) },
                Modifier.padding(top = Spacing.sm),
            )
        }
        SeyirCard {
            CardLabel(stringResource(R.string.settings_language))
            // Dil adları kendi dillerinde yazılır; böylece yanlış dile geçen kullanıcı yolunu bulur.
            SeyirSegmented(
                listOf(stringResource(R.string.settings_system), "English", "Türkçe"),
                language.ordinal,
                { viewModel.setLanguage(AppLanguage.entries[it]) },
                Modifier.padding(top = Spacing.sm),
            )
        }
        SeyirCard(padding = rows) {
            SettingSwitch(R.string.settings_auto_read_receipts, autoReadReceipts, viewModel::setAutoReadReceipts)
        }
        notificationsCard()
        securityCard()
        SeyirCard(padding = rows) {
            SettingRow(
                R.drawable.ic_seyir_lock, stringResource(R.string.settings_data),
                subtitle = stringResource(R.string.settings_data_body),
            )
            HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = divider)
            SettingRow(
                R.drawable.ic_seyir_arrow_out, stringResource(R.string.settings_backup),
                subtitle = stringResource(R.string.receipt_backup_note),
                onClick = { backupPicker.launch("maliyem-yedek-${LocalDate.now()}.json") },
                enabled = !dataBusy,
            )
            HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = divider)
            SettingRow(
                R.drawable.ic_seyir_arrow_in, stringResource(R.string.settings_restore),
                onClick = { restorePicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                enabled = !dataBusy,
            )
            HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = divider)
            SettingRow(
                R.drawable.ic_seyir_note, stringResource(R.string.settings_csv),
                onClick = { csvPicker.launch("maliyem-${LocalDate.now()}.csv") },
                enabled = !dataBusy,
            )
            HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = divider)
            SettingRow(
                R.drawable.ic_seyir_arrow_in, stringResource(R.string.csv_import),
                onClick = {
                    try {
                        csvImportPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
                    } catch (_: ActivityNotFoundException) {
                        scope.launch { snackbar.showSnackbar(resources.getString(R.string.csv_unreadable)) }
                    }
                },
                enabled = !dataBusy,
            )
            HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = divider)
            SettingRow(null, stringResource(R.string.settings_version), value = version)
        }
    }
    pendingBackup?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissBackup,
            title = { Text(stringResource(R.string.settings_restore)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.settings_restore_body,
                        pending.backup.transactions.size, pending.categoryCount,
                        pending.backup.accounts.size, pending.backup.transfers.size, pending.backup.recurringRules.size,
                        pending.backup.budgets.size,
                        pending.backup.overallBudgetMinor?.let {
                            formatMoney(it, resources.configuration.locales[0])
                        } ?: stringResource(R.string.settings_restore_no_budget),
                        pending.backup.savingsGoals.size, pending.backup.goalEntries.size,
                    ) + "\n\n" + stringResource(R.string.receipt_restore_note),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.restoreBackup(restoreWithUndoCleared) }, enabled = !dataBusy) {
                    Text(stringResource(if (dataBusy) R.string.settings_restoring else R.string.settings_restore_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissBackup, enabled = !dataBusy) { Text(stringResource(R.string.add_cancel)) }
            },
        )
    }
}
