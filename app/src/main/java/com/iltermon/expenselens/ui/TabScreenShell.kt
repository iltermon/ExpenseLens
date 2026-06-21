package com.iltermon.expenselens.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabScreenShell(
    title: String,
    viewModel: ExpenseLensViewModel,
    onAdd: (() -> Unit)? = null,
    leftLabel: String,
    leftAmount: Double,
    rightLabel: String,
    rightAmount: Double,
    rightIsNegative: Boolean = false,
    leftRecurring: Double? = null,
    rightRecurring: Double? = null,
    content: @Composable () -> Unit
) {
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    var showRangePicker by remember { mutableStateOf(false) }

    val titleFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

    if (showRangePicker) {
        DateRangePickerDialog(
            onDismiss = { showRangePicker = false },
            onRangeSelected = { start, end ->
                viewModel.selectDateRange(start, end)
                showRangePicker = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = {
                    if (onAdd != null) {
                        IconButton(onClick = onAdd) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                    }
                }
            )
        },
        bottomBar = {
            SummaryBar(
                leftLabel = leftLabel,
                leftAmount = leftAmount,
                rightLabel = rightLabel,
                rightAmount = rightAmount,
                rightIsNegative = rightIsNegative,
                leftRecurring = leftRecurring,
                rightRecurring = rightRecurring
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            MonthSelectorRow(
                selectedMonth = selectedMonth,
                onPrevious = { viewModel.goToPreviousMonth() },
                onNext = { viewModel.goToNextMonth() },
                onCurrentTapped = { showRangePicker = true }
            )
            content()
        }
    }
}

@Composable
private fun MonthSelectorRow(
    selectedMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrentTapped: () -> Unit
) {
    val prevMonth = selectedMonth.minusMonths(1)
    val nextMonth = selectedMonth.plusMonths(1)
    val titleFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = onPrevious, shape = RoundedCornerShape(50)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(prevMonth.format(DateTimeFormatter.ofPattern("MMM")))
        }

        Button(onClick = onCurrentTapped, shape = RoundedCornerShape(50)) {
            Text(selectedMonth.format(titleFormatter), fontWeight = FontWeight.Bold)
        }

        OutlinedButton(onClick = onNext, shape = RoundedCornerShape(50)) {
            Text(nextMonth.format(DateTimeFormatter.ofPattern("MMM")))
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
internal fun SummaryBar(
    leftLabel: String,
    leftAmount: Double,
    rightLabel: String,
    rightAmount: Double,
    rightIsNegative: Boolean,
    leftRecurring: Double? = null,
    rightRecurring: Double? = null
) {
    Surface(tonalElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    leftLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "€${"%.2f".format(leftAmount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                RecurringCaption(leftRecurring)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    rightLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "€${"%.2f".format(rightAmount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (rightIsNegative) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
                RecurringCaption(rightRecurring)
            }
        }
    }
}

@Composable
private fun RecurringCaption(amount: Double?) {
    if (amount != null) {
        Text(
            "↻ €${"%.2f".format(amount)} recurring",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onRangeSelected: (LocalDate, LocalDate) -> Unit
) {
    val state = rememberDateRangePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val start = state.selectedStartDateMillis
                val end = state.selectedEndDateMillis
                if (start != null && end != null) {
                    onRangeSelected(
                        LocalDate.ofEpochDay(start / 86400000),
                        LocalDate.ofEpochDay(end / 86400000)
                    )
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f))
    }
}
