package com.iltermon.expenselens.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.iltermon.expenselens.data.Transaction
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionScreen(
    viewModel: ExpenseLensViewModel,
    transactionId: Int,
    onNavigateBack: () -> Unit
) {
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    var original by remember { mutableStateOf<Transaction?>(null) }
    var prefilled by remember { mutableStateOf(false) }
    val shared = rememberTransactionFormState()

    LaunchedEffect(transactionId) { original = viewModel.getTransactionById(transactionId) }

    val t = original
    val categories = if (t?.isExpense == false) incomeCategories else expenseCategories

    LaunchedEffect(t, categories, accounts) {
        if (t != null && !prefilled && categories.isNotEmpty()) {
            shared.description = t.description
            shared.amount = formatAmount(t.amount)
            shared.selectedCategory = categories.find { it.name == t.category }
            shared.selectedAccount = t.accountId?.let { id -> accounts.find { it.id == id } }
            prefilled = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Transaction") },
                navigationIcon = { TextButton(onClick = onNavigateBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (t == null || !prefilled) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                OneTimeTransactionForm(
                    categories = categories,
                    accounts = accounts,
                    isExpense = t.isExpense,
                    shared = shared,
                    initialDate = LocalDate.parse(t.date),
                    initialIsPaid = t.isPaid,
                    saveLabel = "Update",
                    onSave = { edited ->
                        viewModel.updateTransaction(edited.copy(id = t.id, templateId = t.templateId))
                        onNavigateBack()
                    }
                )
            }
        }
    }
}

/** Renders a stored amount without a trailing ".0" so the edit field reads naturally. */
internal fun formatAmount(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
