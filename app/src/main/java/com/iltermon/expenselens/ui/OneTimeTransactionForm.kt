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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iltermon.expenselens.R
import com.iltermon.expenselens.data.Account
import com.iltermon.expenselens.data.Category
import com.iltermon.expenselens.data.Counterparty
import com.iltermon.expenselens.data.Transaction
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OneTimeTransactionForm(
    categories: List<Category>,
    accounts: List<Account>,
    isExpense: Boolean,
    shared: TransactionFormState,
    onSave: (Transaction, CounterpartyChoice) -> Unit,
    initialDate: LocalDate = LocalDate.now(),
    initialIsPaid: Boolean = true,
    saveLabel: String? = null,
    counterparties: List<Counterparty> = emptyList()
) {
    var description by shared::description
    var amount by shared::amount
    var selectedCategory by shared::selectedCategory
    var selectedAccount by shared::selectedAccount
    var counterpartyName by shared::counterpartyName
    var selectedCounterparty by shared::selectedCounterparty
    var categoryExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(initialDate) }
    var isPaid by remember { mutableStateOf(initialIsPaid) }
    var showErrors by remember { mutableStateOf(false) }
    // Set (with the built transaction + matched counterparty) when a save is waiting on the
    // "update this counterparty's defaults?" prompt.
    var pendingPrompt by remember { mutableStateOf<Pair<Transaction, Counterparty>?>(null) }

    val amountValue = amount.toDoubleOrNull()
    val amountValid = amountValue != null && amountValue > 0
    val descriptionValid = description.isNotBlank()
    val categoryValid = selectedCategory != null
    val accountValid = selectedAccount != null
    val counterpartyValid = counterpartyName.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CounterpartyField(
            value = counterpartyName,
            onValueChange = { counterpartyName = it; selectedCounterparty = null },
            counterparties = counterparties,
            onCounterpartySelected = { cp ->
                selectedCounterparty = cp
                counterpartyName = cp.name
                cp.defaultCategory?.let { name -> selectedCategory = categories.find { it.name == name } }
                cp.defaultAccountId?.let { id -> selectedAccount = accounts.find { it.id == id } }
            },
            isError = showErrors && !counterpartyValid
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text(stringResource(R.string.form_description)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            isError = showErrors && !descriptionValid,
            supportingText = { if (showErrors && !descriptionValid) Text(stringResource(R.string.field_required)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = sanitizeAmountInput(it) },
            label = { Text(stringResource(R.string.form_amount, LocalCurrencySymbol.current)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = showErrors && !amountValid,
            supportingText = { if (showErrors && !amountValid) Text(stringResource(R.string.amount_invalid)) },
            modifier = Modifier.fillMaxWidth()
        )
        DatePickerField(label = stringResource(R.string.form_date), value = date, onValueChange = {
            date = it
            isPaid = !it.isAfter(LocalDate.now())
        })
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(checked = isPaid, onCheckedChange = { isPaid = it })
            Text(stringResource(R.string.form_paid), style = MaterialTheme.typography.bodyMedium)
        }
        ExposedDropdownMenuBox(
            expanded = categoryExpanded,
            onExpandedChange = { categoryExpanded = !categoryExpanded }
        ) {
            OutlinedTextField(
                value = selectedCategory?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.form_category)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                isError = showErrors && !categoryValid,
                supportingText = { if (showErrors && !categoryValid) Text(stringResource(R.string.field_required)) },
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
                value = selectedAccount?.let { accountWithType(it.name, it.type) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.form_account)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                isError = showErrors && !accountValid,
                supportingText = { if (showErrors && !accountValid) Text(stringResource(R.string.field_required)) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) {
                accounts.forEach { acc ->
                    DropdownMenuItem(
                        text = { Text(accountWithType(acc.name, acc.type)) },
                        onClick = { selectedAccount = acc; accountExpanded = false }
                    )
                }
            }
        }
        Button(
            onClick = {
                showErrors = true
                if (!counterpartyValid || !descriptionValid || !amountValid || !categoryValid || !accountValid) return@Button
                val txn = Transaction(
                    description = description,
                    amount = amountValue!!,
                    category = selectedCategory!!.name,
                    date = date.toString(),
                    isExpense = isExpense,
                    isPaid = isPaid,
                    accountId = selectedAccount!!.id
                )
                when (val res = counterpartyChoiceFor(counterpartyName, counterparties, selectedCategory!!.name, selectedAccount!!.id)) {
                    is CounterpartyResolution.Ready -> onSave(txn, res.choice)
                    is CounterpartyResolution.NeedsPrompt -> pendingPrompt = txn to res.existing
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(saveLabel ?: stringResource(if (isExpense) R.string.save_expense else R.string.save_income))
        }
    }

    pendingPrompt?.let { (txn, existing) ->
        UpdateDefaultsDialog(
            name = existing.name,
            onDecision = { update ->
                onSave(txn, CounterpartyChoice.Existing(existing, updateDefaults = update))
                pendingPrompt = null
            },
            onDismiss = { pendingPrompt = null }
        )
    }
}
