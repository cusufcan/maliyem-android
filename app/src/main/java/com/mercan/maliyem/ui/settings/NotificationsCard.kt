package com.mercan.maliyem.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mercan.maliyem.R
import com.mercan.maliyem.ui.components.CardLabel
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SubText
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
    SeyirCard {
        CardLabel(stringResource(R.string.settings_notifications))
        NotificationSwitch(R.string.settings_budget_alerts, budgetAlerts && permissionGranted, canToggle, onBudgetAlertsChange)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NotificationSwitch(
            R.string.settings_recurring_reminders, recurringReminders && permissionGranted, canToggle, onRecurringRemindersChange,
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

@Composable
private fun NotificationSwitch(
    @StringRes title: Int,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(title), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}
