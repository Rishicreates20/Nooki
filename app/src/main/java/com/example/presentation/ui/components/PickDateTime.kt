package com.example.presentation.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.Calendar
import java.util.TimeZone

/** Date first, then time. Android's picker reports the date at UTC midnight, so it is rebuilt locally. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickDateTimeDialog(
    initialMillis: Long,
    onPicked: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val initial = remember(initialMillis) { Calendar.getInstance().apply { timeInMillis = initialMillis } }
    val utcOffset = remember(initialMillis) { TimeZone.getDefault().getOffset(initialMillis).toLong() }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = initialMillis + utcOffset)
    val timeState = rememberTimePickerState(
        initialHour = initial.get(Calendar.HOUR_OF_DAY),
        initialMinute = initial.get(Calendar.MINUTE),
        is24Hour = false
    )
    var choosingTime by remember { mutableStateOf(false) }

    if (!choosingTime) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = { choosingTime = true },
                    enabled = dateState.selectedDateMillis != null
                ) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        ) { DatePicker(state = dateState) }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("What time?") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val date = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                        timeInMillis = dateState.selectedDateMillis ?: initialMillis
                    }
                    val result = Calendar.getInstance().apply {
                        set(date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH), timeState.hour, timeState.minute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onPicked(result.timeInMillis)
                }) { Text("Save time") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }
}
