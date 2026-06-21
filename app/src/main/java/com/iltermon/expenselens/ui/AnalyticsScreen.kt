package com.iltermon.expenselens.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AnalyticsScreen(viewModel: ExpenseLensViewModel) {
    val expenseItems by viewModel.expenseItems.collectAsState()

    val totalExpenses = expenseItems.filter { it.isExpense }.sumOf { it.amount }
    val totalIncome = expenseItems.filter { !it.isExpense }.sumOf { it.amount }

    TabScreenShell(
        title = "Analytics",
        viewModel = viewModel,
        onAdd = null,
        leftLabel = "Total Expenses",
        leftAmount = totalExpenses,
        rightLabel = "Total Income",
        rightAmount = totalIncome,
        rightIsNegative = false
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Analytics coming soon", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
