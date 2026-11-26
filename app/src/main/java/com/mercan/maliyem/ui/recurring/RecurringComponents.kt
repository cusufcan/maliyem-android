package com.mercan.maliyem.ui.recurring

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.RecurringFrequency
import com.mercan.maliyem.ui.components.SettingRow

@StringRes
internal fun RecurringFrequency?.labelRes(): Int = when (this) {
    null -> R.string.recurring_none
    RecurringFrequency.WEEKLY -> R.string.recurring_weekly
    RecurringFrequency.MONTHLY -> R.string.recurring_monthly
    RecurringFrequency.YEARLY -> R.string.recurring_yearly
}

@StringRes
internal fun RecurringFrequency.detailRes(): Int = when (this) {
    RecurringFrequency.WEEKLY -> R.string.recurring_repeats_weekly
    RecurringFrequency.MONTHLY -> R.string.recurring_repeats_monthly
    RecurringFrequency.YEARLY -> R.string.recurring_repeats_yearly
}

@Composable
internal fun FrequencyPicker(
    frequency: RecurringFrequency?,
    onSelect: (RecurringFrequency?) -> Unit,
    includeNone: Boolean = false,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingRow(
            R.drawable.ic_seyir_repeat,
            stringResource(if (includeNone) R.string.recurring_repeat else R.string.recurring_frequency),
            value = stringResource(frequency.labelRes()),
            onClick = { expanded = true },
            horizontalPadding = 0.dp,
            enabled = enabled,
        )
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            val choices = if (includeNone) listOf(null) + RecurringFrequency.entries else RecurringFrequency.entries
            for (choice in choices) {
                DropdownMenuItem(
                    text = { Text(stringResource(choice.labelRes())) },
                    onClick = { onSelect(choice); expanded = false },
                )
            }
        }
    }
}
