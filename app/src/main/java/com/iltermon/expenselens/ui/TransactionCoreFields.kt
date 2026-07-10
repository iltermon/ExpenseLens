package com.iltermon.expenselens.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.iltermon.expenselens.R
import com.iltermon.expenselens.data.Account
import com.iltermon.expenselens.data.Category
import com.iltermon.expenselens.data.Counterparty

/**
 * The fields common to the one-time and recurring transaction forms: counterparty, description,
 * amount, category and account. Emits its children directly into the caller's [androidx.compose.foundation.layout.Column]
 * so the form can append its own type-specific tail below. Reads and writes the hoisted values via
 * [shared] and owns only the transient dropdown-expanded state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionCoreFields(
    categories: List<Category>,
    accounts: List<Account>,
    counterparties: List<Counterparty>,
    shared: TransactionFormState,
    showErrors: Boolean
) {
    var description by shared::description
    var amount by shared::amount
    var selectedCategory by shared::selectedCategory
    var selectedAccount by shared::selectedAccount
    var counterpartyName by shared::counterpartyName
    var selectedCounterparty by shared::selectedCounterparty
    var categoryExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }

    val validation = coreFieldsValid(shared)

    OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text(stringResource(R.string.form_description)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        isError = showErrors && !validation.descriptionValid,
        supportingText = { if (showErrors && !validation.descriptionValid) Text(stringResource(R.string.field_required)) },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = amount,
        onValueChange = { amount = sanitizeAmountInput(it) },
        label = { Text(stringResource(R.string.form_amount, LocalCurrencySymbol.current)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = showErrors && !validation.amountValid,
        supportingText = { if (showErrors && !validation.amountValid) Text(stringResource(R.string.amount_invalid)) },
        modifier = Modifier.fillMaxWidth()
    )
    CounterpartyField(
        value = counterpartyName,
        onValueChange = { counterpartyName = it; selectedCounterparty = null },
        counterparties = counterparties,
        onCounterpartySelected = { cp ->
            selectedCounterparty = cp
            counterpartyName = cp.name
            cp.defaultCategoryId?.let { id -> selectedCategory = categories.find { it.id == id } }
            cp.defaultAccountId?.let { id -> selectedAccount = accounts.find { it.id == id } }
        },
        isError = showErrors && !validation.counterpartyValid
    )
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
            isError = showErrors && !validation.categoryValid,
            supportingText = { if (showErrors && !validation.categoryValid) Text(stringResource(R.string.field_required)) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
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
            isError = showErrors && !validation.accountValid,
            supportingText = { if (showErrors && !validation.accountValid) Text(stringResource(R.string.field_required)) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
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
}

/**
 * Validity of the shared core fields, plus the parsed amount so a save button need not re-parse it.
 * Single source of truth for both the per-field error display in [TransactionCoreFields] and the
 * submit guard in each form.
 */
internal data class CoreFieldsValidation(
    val amountValue: Double?,
    val amountValid: Boolean,
    val descriptionValid: Boolean,
    val categoryValid: Boolean,
    val accountValid: Boolean,
    val counterpartyValid: Boolean
) {
    val isValid: Boolean
        get() = amountValid && descriptionValid && categoryValid && accountValid && counterpartyValid
}

internal fun coreFieldsValid(shared: TransactionFormState): CoreFieldsValidation {
    val amountValue = shared.amount.toDoubleOrNull()
    return CoreFieldsValidation(
        amountValue = amountValue,
        amountValid = amountValue != null && amountValue > 0,
        descriptionValid = shared.description.isNotBlank(),
        categoryValid = shared.selectedCategory != null,
        accountValid = shared.selectedAccount != null,
        counterpartyValid = shared.counterpartyName.isNotBlank()
    )
}

/**
 * Drives the shared "resolve the typed counterparty, and prompt before re-curating a known one"
 * save flow used by both forms. Generic over the entity being saved ([Transaction] or
 * [RecurringTemplate]). Pair with [CounterpartySavePromptDialog] to render the confirmation.
 */
@Stable
internal class CounterpartySaveController<T> {
    var pending by mutableStateOf<Pair<T, Counterparty>?>(null)
        private set

    /** Resolves the counterparty and either saves immediately or arms the defaults prompt. */
    fun submit(
        entity: T,
        name: String,
        counterparties: List<Counterparty>,
        categoryId: Int?,
        accountId: Int?,
        onSave: (T, CounterpartyChoice) -> Unit
    ) {
        when (val res = counterpartyChoiceFor(name, counterparties, categoryId, accountId)) {
            is CounterpartyResolution.Ready -> onSave(entity, res.choice)
            is CounterpartyResolution.NeedsPrompt -> pending = entity to res.existing
        }
    }

    fun dismiss() {
        pending = null
    }
}

@Composable
internal fun <T> rememberCounterpartySaveController(): CounterpartySaveController<T> =
    remember { CounterpartySaveController() }

/** Renders the "update this counterparty's defaults?" dialog while [controller] has a pending save. */
@Composable
internal fun <T> CounterpartySavePromptDialog(
    controller: CounterpartySaveController<T>,
    onSave: (T, CounterpartyChoice) -> Unit
) {
    controller.pending?.let { (entity, existing) ->
        UpdateDefaultsDialog(
            name = existing.name,
            onDecision = { update ->
                onSave(entity, CounterpartyChoice.Existing(existing, updateDefaults = update))
                controller.dismiss()
            },
            onDismiss = { controller.dismiss() }
        )
    }
}
