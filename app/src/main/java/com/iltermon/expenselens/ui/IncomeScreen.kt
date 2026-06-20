package com.iltermon.expenselens.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun IncomeScreen(
    viewModel: ExpenseLensViewModel,
    onAddIncome: () -> Unit
) {
    val expenseItems by viewModel.expenseItems.collectAsState()

    val items = expenseItems.filter { !it.isExpense }
    val receivedItems = items.filter { it.isPaid }
    val pendingItems = items.filter { !it.isPaid }

    TabScreenShell(
        title = "Income",
        viewModel = viewModel,
        onAdd = onAddIncome,
        leftLabel = "This Month Income",
        leftAmount = items.sumOf { it.amount },
        rightLabel = "Pending Income",
        rightAmount = pendingItems.sumOf { it.amount },
        rightIsNegative = false
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (receivedItems.isNotEmpty()) {
                item { SectionHeader(title = "Received", total = receivedItems.sumOf { it.amount }) }
                items(receivedItems) { item ->
                    ExpenseItemCard(item = item, onTogglePaid = { viewModel.togglePaid(it) })
                }
            }

            if (pendingItems.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader(title = "Pending", total = pendingItems.sumOf { it.amount })
                }
                items(pendingItems) { item ->
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
                        Text("No income for this period.")
                    }
                }
            }
        }
    }
}
