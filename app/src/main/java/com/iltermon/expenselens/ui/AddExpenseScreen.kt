package com.iltermon.expenselens.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.iltermon.expenselens.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    viewModel: ExpenseLensViewModel,
    onNavigateBack: () -> Unit
) {
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val sharedFields = rememberTransactionFormState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_expense_title)) },
                navigationIcon = { BackButton(onClick = onNavigateBack) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.tab_one_time)) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.tab_recurring)) })
            }

            if (selectedTab == 0) {
                OneTimeTransactionForm(
                    categories = expenseCategories,
                    accounts = accounts,
                    isExpense = true,
                    shared = sharedFields,
                    onSave = { transaction ->
                        viewModel.insertTransaction(transaction)
                        onNavigateBack()
                    }
                )
            } else {
                RecurringForm(
                    categories = expenseCategories,
                    accounts = accounts,
                    isExpense = true,
                    shared = sharedFields,
                    onSave = { template ->
                        viewModel.insertTemplate(template)
                        onNavigateBack()
                    }
                )
            }
        }
    }
}
