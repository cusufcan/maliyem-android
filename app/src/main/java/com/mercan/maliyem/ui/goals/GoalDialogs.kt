package com.mercan.maliyem.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.SavingsGoalEntity
import com.mercan.maliyem.ui.add.amountInputText
import com.mercan.maliyem.ui.add.parseAmount
import com.mercan.maliyem.ui.categories.CategoryIconPicker
import com.mercan.maliyem.ui.components.DateSelectionDialog
import com.mercan.maliyem.ui.components.SubText
import com.mercan.maliyem.ui.theme.Spacing
import java.time.LocalDate

@Composable
internal fun EditGoalDialog(
    goal: SavingsGoalEntity?,
    today: LocalDate,
    write: GoalWriteState,
    onDismiss: () -> Unit,
    onSave: (String, Long, LocalDate?, String) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    var name by rememberSaveable { mutableStateOf(goal?.name.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(goal?.let { amountInputText(it.targetMinor, locale) }.orEmpty()) }
    var deadlineDay by rememberSaveable { mutableStateOf(goal?.deadline?.toEpochDay()) }
    var icon by rememberSaveable { mutableStateOf(goal?.icon ?: "piggy") }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val deadline = deadlineDay?.let(LocalDate::ofEpochDay)
    val minor = parseAmount(amount, locale)?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = { if (!write.busy) onDismiss() },
        title = { Text(stringResource(if (goal == null) R.string.goal_new else R.string.goal_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    name, { name = it }, Modifier.fillMaxWidth(), enabled = !write.busy,
                    label = { Text(stringResource(R.string.goal_name)) }, singleLine = true,
                )
                GoalAmountField(amount, { amount = it }, R.string.goal_target, minor, write.busy)
                FlowRow {
                    TextButton({ pickingDate = true }, enabled = !write.busy) {
                        Text(deadline?.let { stringResource(R.string.goal_deadline_value, goalDate(it)) }
                            ?: stringResource(R.string.goal_set_deadline))
                    }
                    if (deadline != null) {
                        TextButton({ deadlineDay = null }, enabled = !write.busy) {
                            Text(stringResource(R.string.goal_clear_deadline))
                        }
                    }
                }
                SubText(stringResource(R.string.categories_icon))
                CategoryIconPicker(icon) { if (!write.busy) icon = it }
                GoalWriteError(write.error)
            }
        },
        confirmButton = {
            TextButton({ minor?.let { onSave(name, it, deadline, icon) } },
                enabled = !write.busy && name.isNotBlank() && minor != null) {
                Text(stringResource(R.string.add_save))
            }
        },
        dismissButton = { CancelGoalAction(onDismiss, write.busy) },
    )
    if (pickingDate) {
        DateSelectionDialog(deadline ?: today, onDismiss = { pickingDate = false }, onPick = { deadlineDay = it.toEpochDay() })
    }
}

@Composable
internal fun GoalEntryDialog(
    withdraw: Boolean,
    today: LocalDate,
    write: GoalWriteState,
    onDismiss: () -> Unit,
    onSave: (Long, LocalDate, String) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    var amount by rememberSaveable { mutableStateOf("") }
    var entryDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    val date = LocalDate.ofEpochDay(entryDay)
    val minor = parseAmount(amount, locale)?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = { if (!write.busy) onDismiss() },
        title = { Text(stringResource(if (withdraw) R.string.goal_withdraw else R.string.goal_deposit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GoalAmountField(amount, { amount = it }, R.string.add_amount, minor, write.busy)
                TextButton({ pickingDate = true }, enabled = !write.busy) {
                    Text(stringResource(R.string.goal_entry_date_value, goalDate(date)))
                }
                OutlinedTextField(
                    note, { note = it }, Modifier.fillMaxWidth(), enabled = !write.busy,
                    label = { Text(stringResource(R.string.goal_note_optional)) }, maxLines = 3,
                )
                GoalWriteError(write.error)
            }
        },
        confirmButton = {
            TextButton({ minor?.let { onSave(if (withdraw) -it else it, date, note) } },
                enabled = !write.busy && minor != null && date <= today) {
                Text(stringResource(R.string.add_save))
            }
        },
        dismissButton = { CancelGoalAction(onDismiss, write.busy) },
    )
    if (pickingDate) {
        DateSelectionDialog(date, onDismiss = { pickingDate = false }, onPick = { entryDay = it.toEpochDay() }, latest = today)
    }
}

@Composable
internal fun DeleteGoalDialog(name: String, write: GoalWriteState, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!write.busy) onDismiss() },
        title = { Text(stringResource(R.string.goal_delete_title, name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.goal_delete_body))
                GoalWriteError(write.error)
            }
        },
        confirmButton = {
            TextButton(onDelete, enabled = !write.busy) {
                Text(stringResource(R.string.detail_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { CancelGoalAction(onDismiss, write.busy) },
    )
}

@Composable
private fun GoalAmountField(text: String, onChange: (String) -> Unit, label: Int, minor: Long?, busy: Boolean) {
    val invalid = text.isNotBlank() && minor == null
    OutlinedTextField(
        text, onChange, Modifier.fillMaxWidth(), enabled = !busy,
        label = { Text(stringResource(label)) },
        supportingText = if (invalid) ({ Text(stringResource(R.string.goal_amount_invalid)) }) else null,
        isError = invalid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
    )
}

@Composable
private fun CancelGoalAction(onDismiss: () -> Unit, busy: Boolean) {
    TextButton(onDismiss, enabled = !busy) { Text(stringResource(R.string.add_cancel)) }
}

@Composable
internal fun GoalWriteError(error: Int?) {
    if (error == null) return
    Text(
        stringResource(error), Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
    )
}
