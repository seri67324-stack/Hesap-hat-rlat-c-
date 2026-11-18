package com.harcamatakip.app.presentation.components

import androidx.compose.foundation.layout.height
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

private const val DAY_MILLIS = 86_400_000L

/** Tek tarih seçici. Seçilen gün epochDay olarak döner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleDatePickerDialog(
    initialEpochDay: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialEpochDay * DAY_MILLIS)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) onConfirm(millis / DAY_MILLIS) else onDismiss()
            }) { Text("Tamam") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    ) {
        DatePicker(state = state)
    }
}

/** Tarih aralığı seçici. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialogM3(
    initial: Pair<LocalDate, LocalDate>?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.first?.toEpochDay()?.times(DAY_MILLIS),
        initialSelectedEndDateMillis = initial?.second?.toEpochDay()?.times(DAY_MILLIS)
    )
    val start = state.selectedStartDateMillis
    val end = state.selectedEndDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = start != null && end != null,
                onClick = {
                    if (start != null && end != null) {
                        onConfirm(
                            LocalDate.ofEpochDay(start / DAY_MILLIS),
                            LocalDate.ofEpochDay(end / DAY_MILLIS)
                        )
                    }
                }
            ) { Text("Tamam") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    ) {
        DateRangePicker(state = state, modifier = Modifier.height(500.dp))
    }
}
