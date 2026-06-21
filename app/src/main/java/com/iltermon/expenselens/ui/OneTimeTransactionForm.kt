package com.iltermon.expenselens.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iltermon.expenselens.data.Account
import com.iltermon.expenselens.data.Category
import com.iltermon.expenselens.data.Transaction
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OneTimeTransactionForm(
    categories: List<Category>,
    accounts: List<Account>,
    isExpense: Boolean,
    shared: TransactionFormState,
    onSave: (Transaction) -> Unit,
    initialDate: LocalDate = LocalDate.now(),
    initialIsPaid: Boolean = true,
    saveLabel: String? = null
) {
    var description by shared::description
    var amount by shared::amount
    var selectedCategory by shared::selectedCategory
    var selectedAccount by shared::selectedAccount
    var categoryExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(initialDate) }
    var isPaid by remember { mutableStateOf(initialIsPaid) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount (€)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        DatePickerField(label = "Date", value = date, onValueChange = {
            date = it
            isPaid = (it == LocalDate.now())
        })
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(checked = isPaid, onCheckedChange = { isPaid = it })
            Text("Paid", style = MaterialTheme.typography.bodyMedium)
        }
        ExposedDropdownMenuBox(
            expanded = categoryExpanded,
            onExpandedChange = { categoryExpanded = !categoryExpanded }
        ) {
            OutlinedTextField(
                value = selectedCategory?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                categories.forEach { cat ->
                    DropdownMenuItem(text = { Text(cat.name) }, onClick = { selectedCategory = cat; categoryExpanded = false })
                }
            }
        }
        ExposedDropdownMenuBox(
            expanded = accountExpanded,
            onExpandedChange = { accountExpanded = !accountExpanded }
        ) {
            OutlinedTextField(
                value = selectedAccount?.let { "${it.name} (${it.type})" } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Account (optional)") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) {
                DropdownMenuItem(text = { Text("None") }, onClick = { selectedAccount = null; accountExpanded = false })
                accounts.forEach { acc ->
                    DropdownMenuItem(
                        text = { Text("${acc.name} (${acc.type})") },
                        onClick = { selectedAccount = acc; accountExpanded = false }
                    )
                }
            }
        }
        Button(
            onClick = {
                val parsedAmount = amount.toDoubleOrNull() ?: return@Button
                val categoryName = selectedCategory?.name ?: return@Button
                if (description.isBlank()) return@Button
                onSave(
                    Transaction(
                        description = description,
                        amount = parsedAmount,
                        category = categoryName,
                        date = date.toString(),
                        isExpense = isExpense,
                        isPaid = isPaid,
                        accountId = selectedAccount?.id
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(saveLabel ?: if (isExpense) "Save Expense" else "Save Income")
        }
    }
}
