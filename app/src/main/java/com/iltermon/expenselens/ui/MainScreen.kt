package com.iltermon.expenselens.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iltermon.expenselens.data.Transaction
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: ExpenseLensViewModel, onAddTransaction: () -> Unit) {
    val transactions by viewModel.monthlyTransactions.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("ExpenseLens") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTransaction) {
                Text("+")
            }
        }
    ) { padding ->
        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No transactions yet. Tap + to add one.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions) { transaction ->
                    TransactionItem(transaction = transaction)
                }
            }
        }
    }
}

@Composable
fun TransactionItem(transaction: Transaction) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(transaction.description, style = MaterialTheme.typography.bodyLarge)
                Text(transaction.category, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = "${if (transaction.isExpense) "-" else "+"}€${transaction.amount}",
                color = if (transaction.isExpense) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        }
    }
}