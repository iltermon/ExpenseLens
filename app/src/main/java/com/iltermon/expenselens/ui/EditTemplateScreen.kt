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
import com.iltermon.expenselens.data.RecurringTemplate
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTemplateScreen(
    viewModel: ExpenseLensViewModel,
    templateId: Int,
    onNavigateBack: () -> Unit
) {
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    var original by remember { mutableStateOf<RecurringTemplate?>(null) }
    var prefilled by remember { mutableStateOf(false) }
    val shared = rememberTransactionFormState()

    LaunchedEffect(templateId) { original = viewModel.getTemplateById(templateId) }

    val template = original
    val categories = if (template?.isExpense == false) incomeCategories else expenseCategories

    LaunchedEffect(template, categories, accounts) {
        if (template != null && !prefilled && categories.isNotEmpty()) {
            shared.description = template.description
            shared.amount = formatAmount(template.amount)
            shared.selectedCategory = categories.find { it.name == template.category }
            shared.selectedAccount = template.accountId?.let { id -> accounts.find { it.id == id } }
            prefilled = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Recurring") },
                navigationIcon = { TextButton(onClick = onNavigateBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (template == null || !prefilled) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                RecurringForm(
                    categories = categories,
                    accounts = accounts,
                    isExpense = template.isExpense,
                    shared = shared,
                    initialStartDate = LocalDate.parse(template.startDate),
                    initialEndDate = template.endDate?.let { LocalDate.parse(it) },
                    initialInterval = template.frequencyInterval,
                    initialUnit = template.frequencyUnit,
                    initialAutoPayment = template.autoPayment,
                    saveLabel = "Update",
                    onSave = { edited ->
                        viewModel.updateTemplate(edited.copy(id = template.id))
                        onNavigateBack()
                    }
                )
            }
        }
    }
}
