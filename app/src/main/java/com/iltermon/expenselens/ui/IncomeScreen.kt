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
    onAddIncome: () -> Unit,
    onEditTransaction: (Int) -> Unit,
    onEditTemplate: (Int) -> Unit
) {
    val expenseItems by viewModel.expenseItems.collectAsState()
    val templates by viewModel.allTemplates.collectAsState()

    val items = expenseItems.filter { !it.isExpense }
    val recurringItems = items.filter { it.isRecurring && !it.isPaid }
    val pendingItems = items.filter { !it.isRecurring && !it.isPaid }
    val receivedItems = items.filter { it.isPaid }

    TabScreenShell(
        title = "Income",
        viewModel = viewModel,
        onAdd = onAddIncome,
        leftLabel = "This Month Income",
        leftAmount = items.sumOf { it.amount },
        rightLabel = "Pending Income",
        rightAmount = (recurringItems + pendingItems).sumOf { it.amount },
        rightIsNegative = false
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (recurringItems.isNotEmpty()) {
                item { SectionHeader(title = "Recurring", total = recurringItems.sumOf { it.amount }) }
                items(recurringItems) { item ->
                    ExpenseItemRow(item, templates, viewModel, onEditTransaction, onEditTemplate)
                }
            }

            if (pendingItems.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader(title = "Pending", total = pendingItems.sumOf { it.amount })
                }
                items(pendingItems) { item ->
                    ExpenseItemRow(item, templates, viewModel, onEditTransaction, onEditTemplate)
                }
            }

            if (receivedItems.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader(title = "Received", total = receivedItems.sumOf { it.amount })
                }
                items(receivedItems) { item ->
                    ExpenseItemRow(item, templates, viewModel, onEditTransaction, onEditTemplate)
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
