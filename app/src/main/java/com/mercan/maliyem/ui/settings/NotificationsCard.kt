package com.mercan.maliyem.ui.settings

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mercan.maliyem.R
import com.mercan.maliyem.ui.components.CardLabel
import com.mercan.maliyem.ui.components.SettingSwitch
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SubText

@Composable
internal fun NotificationsCard(
    budgetAlerts: Boolean,
    recurringReminders: Boolean,
    permissionGranted: Boolean,
    canRequestPermission: Boolean,
    onBudgetAlertsChange: (Boolean) -> Unit,
    onRecurringRemindersChange: (Boolean) -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    val canToggle = permissionGranted || canRequestPermission
    SeyirCard {
        CardLabel(stringResource(R.string.settings_notifications))
        SettingSwitch(R.string.settings_budget_alerts, budgetAlerts && permissionGranted, onBudgetAlertsChange, enabled = canToggle)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingSwitch(
            R.string.settings_recurring_reminders, recurringReminders && permissionGranted, onRecurringRemindersChange,
            enabled = canToggle,
        )
        if (!permissionGranted) {
            SubText(stringResource(if (canRequestPermission) R.string.notifications_permission_needed else R.string.notifications_blocked))
            if (!canRequestPermission) {
                TextButton(onClick = onOpenSystemSettings) {
                    Text(stringResource(R.string.notifications_open_settings))
                }
            }
        }
    }
}
