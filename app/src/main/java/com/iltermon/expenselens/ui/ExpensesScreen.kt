package com.iltermon.expenselens.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ExpensesScreen(
    viewModel: ExpenseLensViewModel,
    onAddTransaction: () -> Unit
) {
    val expenseItems by viewModel.expenseItems.collectAsState()

    val items = expenseItems.filter { it.isExpense }
    val paidItems = items.filter { it.isPaid }
    val unpaidItems = items.filter { !it.isPaid }

    TabScreenShell(
        title = "Expenses",
        viewModel = viewModel,
        onAdd = onAddTransaction,
        leftLabel = "This Month Expenses",
        leftAmount = items.sumOf { it.amount },
        rightLabel = "Remaining Payment",
        rightAmount = unpaidItems.sumOf { it.amount },
        rightIsNegative = true
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (paidItems.isNotEmpty()) {
                item { SectionHeader(title = "Paid", total = paidItems.sumOf { it.amount }) }
                items(paidItems) { item ->
                    ExpenseItemCard(item = item, onTogglePaid = { viewModel.togglePaid(it) })
                }
            }

            if (unpaidItems.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader(title = "To be paid", total = unpaidItems.sumOf { it.amount })
                }
                items(unpaidItems) { item ->
                    ExpenseItemCard(item = item, onTogglePaid = { viewModel.togglePaid(it) })
                }
            }

            if (items.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No expenses for this period.")
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
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    item.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

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
