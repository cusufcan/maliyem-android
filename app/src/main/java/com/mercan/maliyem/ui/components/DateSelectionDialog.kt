package com.mercan.maliyem.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.mercan.maliyem.R
import java.time.LocalDate

/** Takvimde günler UTC gece yarısıyla temsil edilir; saat dilimi günü değiştirmez. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateSelectionDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
    earliest: LocalDate? = null,
    latest: LocalDate? = null,
) {
    val initialDate = initial.coerceIn(earliest ?: LocalDate.MIN, latest ?: LocalDate.MAX)
        .coerceIn(LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31))
    val lastYear = (latest?.year ?: initialDate.year).coerceIn(1, 9999)
    val range = minOf(1900, initialDate.year)..maxOf(2100, initialDate.year, lastYear)
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toEpochDay() * MILLIS_PER_DAY,
        yearRange = range,
        selectableDates = remember(earliest, latest) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val day = LocalDate.ofEpochDay(Math.floorDiv(utcTimeMillis, MILLIS_PER_DAY))
                    return (earliest == null || day >= earliest) && (latest == null || day <= latest)
                }

                override fun isSelectableYear(year: Int) =
                    (earliest == null || year >= earliest.year) && (latest == null || year <= latest.year)
            }
        },
    )
    val selected = picker.selectedDateMillis?.let { LocalDate.ofEpochDay(Math.floorDiv(it, MILLIS_PER_DAY)) }
        ?.takeIf { (earliest == null || it >= earliest) && (latest == null || it <= latest) }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { selected?.let(onPick); onDismiss() },
                enabled = selected != null,
            ) { Text(stringResource(R.string.add_pick)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.add_cancel)) } },
    ) { DatePicker(picker) }
}

private const val MILLIS_PER_DAY = 86_400_000L
