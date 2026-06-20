package com.iltermon.expenselens.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    viewModel: ExpenseLensViewModel,
    onAddTransaction: () -> Unit
) {
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val dateRange by viewModel.dateRange.collectAsState()
    val expenseItems by viewModel.expenseItems.collectAsState()
    var showRangePicker by remember { mutableStateOf(false) }

    val paidItems = expenseItems.filter { it.isPaid }
    val unpaidItems = expenseItems.filter { !it.isPaid }

    val totalPaid = paidItems.filter { it.isExpense }.sumOf { it.amount }
    val totalUnpaid = unpaidItems.filter { it.isExpense }.sumOf { it.amount }
    val totalMonthExpenses = totalPaid + totalUnpaid

    val prevMonth = selectedMonth.minusMonths(1)
    val nextMonth = selectedMonth.plusMonths(1)
    val monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy")
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
                title = { Text("Expenses") },
                actions = {
                    IconButton(onClick = onAddTransaction) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Add,
                            contentDescription = "Add transaction"
                        )
                    }
                }
            )
        },
        bottomBar = {
            ExpensesSummaryBar(
                totalMonth = totalMonthExpenses,
                remainingUnpaid = totalUnpaid
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Month navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous month pill
                OutlinedButton(
                    onClick = { viewModel.goToPreviousMonth() },
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(prevMonth.format(DateTimeFormatter.ofPattern("MMM")))
                }

                // Current month button
                Button(
                    onClick = { showRangePicker = true },
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        selectedMonth.format(titleFormatter),
                        fontWeight = FontWeight.Bold
                    )
                }

                // Next month pill
                OutlinedButton(
                    onClick = { viewModel.goToNextMonth() },
                    shape = RoundedCornerShape(50)
                ) {
                    Text(nextMonth.format(DateTimeFormatter.ofPattern("MMM")))
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Paid section
                if (paidItems.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Paid",
                            total = paidItems.filter { it.isExpense }.sumOf { it.amount }
                        )
                    }
                    items(paidItems) { item ->
                        ExpenseItemCard(item = item, onTogglePaid = { viewModel.togglePaid(it) })
                    }
                }

                // To be paid section
                if (unpaidItems.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        SectionHeader(
                            title = "To be paid",
                            total = unpaidItems.filter { it.isExpense }.sumOf { it.amount }
                        )
                    }
                    items(unpaidItems) { item ->
                        ExpenseItemCard(item = item, onTogglePaid = { viewModel.togglePaid(it) })
                    }
                }

                if (expenseItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No transactions for this period.")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, total: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "€${"%.2f".format(total)}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ExpenseItemCard(item: ExpenseItem, onTogglePaid: (ExpenseItem) -> Unit) {
    val dateFormatter = DateTimeFormatter.ofPattern("d MMM")
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isPaid)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Paid/unpaid badge button
            FilledIconButton(
                onClick = { onTogglePaid(item) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (item.isPaid)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.tertiary
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Text(if (item.isPaid) "✓" else "−")
            }

            // Description + category
            Column(modifier = Modifier.weight(1f)) {
                Text(item.description, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(item.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Amount + date
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "€${"%.2f".format(item.amount)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.isExpense) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
                Text(
                    LocalDate.parse(item.date).format(dateFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ExpensesSummaryBar(totalMonth: Double, remainingUnpaid: Double) {
    Surface(tonalElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("This Month Total", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "€${"%.2f".format(totalMonth)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Remaining Payment", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "€${"%.2f".format(remainingUnpaid)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
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
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f))
    }
}