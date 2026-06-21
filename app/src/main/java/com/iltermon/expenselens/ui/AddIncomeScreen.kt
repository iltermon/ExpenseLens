package com.iltermon.expenselens.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddIncomeScreen(
    viewModel: ExpenseLensViewModel,
    onNavigateBack: () -> Unit
) {
    val incomeCategories by viewModel.incomeCategories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Income") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("One-time") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Recurring") })
            }

            if (selectedTab == 0) {
                OneTimeTransactionForm(
                    categories = incomeCategories,
                    accounts = accounts,
                    isExpense = false,
                    onSave = { transaction ->
                        viewModel.insertTransaction(transaction)
                        onNavigateBack()
                    }
                )
            } else {
                RecurringForm(
                    categories = incomeCategories,
                    accounts = accounts,
                    isExpense = false,
                    onSave = { template ->
                        viewModel.insertTemplate(template)
                        onNavigateBack()
                    }
                )
            }
        }
    }
}
