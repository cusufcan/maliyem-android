package com.mercan.maliyem.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mercan.maliyem.R
import com.mercan.maliyem.ui.components.CardLabel
import com.mercan.maliyem.ui.components.SettingSwitch
import com.mercan.maliyem.ui.components.SeyirActionButton
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SubText
import com.mercan.maliyem.ui.theme.Motion
import com.mercan.maliyem.ui.theme.Spacing

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
    SeyirCard(padding = PaddingValues()) {
        Column(Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg)) {
            CardLabel(stringResource(R.string.settings_notifications))
        }
        SettingSwitch(R.string.settings_budget_alerts, budgetAlerts && permissionGranted, onBudgetAlertsChange, enabled = canToggle)
        HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = MaterialTheme.colorScheme.outlineVariant)
        SettingSwitch(
            R.string.settings_recurring_reminders, recurringReminders && permissionGranted, onRecurringRemindersChange,
            enabled = canToggle,
        )
        AnimatedVisibility(!permissionGranted, enter = Motion.expand(), exit = Motion.collapse()) {
            Column(Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg)) {
                SubText(stringResource(if (canRequestPermission) R.string.notifications_permission_needed else R.string.notifications_blocked))
                if (!canRequestPermission) {
                    SeyirActionButton(
                        stringResource(R.string.notifications_open_settings), onOpenSystemSettings,
                        icon = R.drawable.ic_seyir_nav_settings,
                    )
                }
            }
        }
    }
}
