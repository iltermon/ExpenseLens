package com.iltermon.expenselens.ui.transactions

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.iltermon.expenselens.R
import com.iltermon.expenselens.ui.components.BackButton
import com.iltermon.expenselens.ui.ExpenseLensViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    viewModel: ExpenseLensViewModel,
    type: TransactionType,
    onNavigateBack: () -> Unit
) {
    val categories by when (type) {
        TransactionType.EXPENSE -> viewModel.activeExpenseCategories
        TransactionType.INCOME -> viewModel.activeIncomeCategories
    }.collectAsState()
    val accounts by viewModel.activeAccounts.collectAsState()
    val counterparties by viewModel.counterparties.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    // One date for both tabs: the one-time date becomes the recurring start date and vice versa.
    var date by remember { mutableStateOf(LocalDate.now()) }
    val sharedFields = rememberTransactionFormState()
    val gate = rememberFormBackGate()
    val sharedBaseline = rememberSharedBaseline(sharedFields, ready = true)
    val onBack = rememberUnsavedChangesBackGuard(gate, onNavigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(type.addTitle)) },
                navigationIcon = { BackButton(onClick = onBack) }
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
                    initialDate = date,
                    onDateChange = { date = it },
                    categories = categories,
                    accounts = accounts,
                    isExpense = type.isExpense,
                    shared = sharedFields,
                    counterparties = counterparties,
                    gate = gate,
                    sharedBaseline = sharedBaseline,
                    onSave = { transaction, choice ->
                        viewModel.saveTransaction(transaction, choice)
                        onNavigateBack()
                    }
                )
            } else {
                RecurringForm(
                    initialStartDate = date,
                    onStartDateChange = { date = it },
                    categories = categories,
                    accounts = accounts,
                    isExpense = type.isExpense,
                    shared = sharedFields,
                    counterparties = counterparties,
                    gate = gate,
                    sharedBaseline = sharedBaseline,
                    onSave = { template, choice ->
                        viewModel.saveTemplate(template, choice)
                        onNavigateBack()
                    }
                )
            }
        }
    }
}
