package com.iltermon.expenselens.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iltermon.expenselens.R
import com.iltermon.expenselens.data.Account
import com.iltermon.expenselens.data.Category
import com.iltermon.expenselens.data.Counterparty
import com.iltermon.expenselens.data.Transaction
import java.time.LocalDate

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
    var date by remember { mutableStateOf(initialDate) }
    var isPaid by remember { mutableStateOf(initialIsPaid) }
    var showErrors by remember { mutableStateOf(false) }
    val saveController = rememberCounterpartySaveController<Transaction>()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TransactionCoreFields(
            categories = categories,
            accounts = accounts,
            counterparties = counterparties,
            shared = shared,
            showErrors = showErrors
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
        Button(
            onClick = {
                showErrors = true
                val validation = coreFieldsValid(shared)
                if (!validation.isValid) return@Button
                val txn = Transaction(
                    description = shared.description,
                    amount = validation.amountValue!!,
                    category = shared.selectedCategory!!.name,
                    date = date.toString(),
                    isExpense = isExpense,
                    isPaid = isPaid,
                    accountId = shared.selectedAccount!!.id
                )
                saveController.submit(
                    entity = txn,
                    name = shared.counterpartyName,
                    counterparties = counterparties,
                    category = shared.selectedCategory!!.name,
                    accountId = shared.selectedAccount!!.id,
                    onSave = onSave
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(saveLabel ?: stringResource(if (isExpense) R.string.save_expense else R.string.save_income))
        }
    }

    CounterpartySavePromptDialog(controller = saveController, onSave = onSave)
}
