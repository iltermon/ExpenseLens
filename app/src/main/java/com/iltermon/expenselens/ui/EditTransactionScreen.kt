package com.iltermon.expenselens.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import com.iltermon.expenselens.R
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
    val counterparties by viewModel.counterparties.collectAsState()

    var original by remember { mutableStateOf<Transaction?>(null) }
    var prefilled by remember { mutableStateOf(false) }
    val shared = rememberTransactionFormState()

    LaunchedEffect(transactionId) { original = viewModel.getTransactionById(transactionId) }

    val t = original
    val categories = if (t?.isExpense == false) incomeCategories else expenseCategories

    // Seed every field in one pass so `prefilled` implies the baseline is complete — including the
    // counterparty, which may resolve a frame later than the categories/accounts lists load.
    LaunchedEffect(t, categories, accounts, counterparties) {
        if (t != null && !prefilled && categories.isNotEmpty()) {
            val cp = t.counterpartyId?.let { id -> counterparties.find { it.id == id } }
            if (t.counterpartyId != null && cp == null) return@LaunchedEffect
            shared.description = t.description
            shared.amount = formatAmount(t.amount)
            shared.selectedCategory = categories.find { it.id == t.categoryId }
            shared.selectedAccount = t.accountId?.let { id -> accounts.find { it.id == id } }
            if (cp != null) {
                shared.selectedCounterparty = cp
                shared.counterpartyName = cp.name
            }
            prefilled = true
        }
    }

    val gate = rememberFormBackGate()
    val sharedBaseline = rememberSharedBaseline(shared, ready = prefilled)
    val onBack = rememberUnsavedChangesBackGuard(gate, onNavigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_transaction_title)) },
                navigationIcon = { BackButton(onClick = onBack) }
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
                    counterparties = counterparties,
                    saveLabel = stringResource(R.string.action_update),
                    gate = gate,
                    sharedBaseline = sharedBaseline,
                    onSave = { edited, choice ->
                        viewModel.updateTransaction(edited.copy(id = t.id, templateId = t.templateId), choice)
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
