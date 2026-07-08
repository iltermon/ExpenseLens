package com.iltermon.expenselens.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iltermon.expenselens.R
import com.iltermon.expenselens.data.Account
import com.iltermon.expenselens.data.Category
import com.iltermon.expenselens.data.Counterparty

/**
 * Screen-scoped bridge that lets the screen-level back guard ask the currently visible form
 * whether it is dirty and to run its exact Save path. The form assigns these lambdas in a
 * [androidx.compose.runtime.SideEffect] so they always close over its latest local state and
 * params. They are invoked only at event time (back press / dialog Save), never read during
 * composition, so plain `var` (no snapshot state) is sufficient.
 */
@Stable
class FormBackGate {
    var isDirty: () -> Boolean = { false }
    var requestSave: () -> Unit = {}
}

@Composable
fun rememberFormBackGate(): FormBackGate = remember { FormBackGate() }

/**
 * Immutable snapshot of the hoisted [TransactionFormState] captured once the form's initial values
 * are settled (immediately for Add; after prefill for Edit). Lives at screen scope so it survives
 * the Add screens' tab switch and is not re-captured when a form is recreated.
 */
@Stable
class SharedBaseline private constructor(
    private val description: String,
    private val amount: String,
    private val category: Category?,
    private val account: Account?,
    private val counterpartyName: String,
    private val counterparty: Counterparty?
) {
    fun isDirty(s: TransactionFormState): Boolean =
        s.description != description ||
            s.amount != amount ||
            s.selectedCategory != category ||
            s.selectedAccount != account ||
            s.counterpartyName != counterpartyName ||
            s.selectedCounterparty != counterparty

    companion object {
        fun from(s: TransactionFormState) = SharedBaseline(
            description = s.description,
            amount = s.amount,
            category = s.selectedCategory,
            account = s.selectedAccount,
            counterpartyName = s.counterpartyName,
            counterparty = s.selectedCounterparty
        )
    }
}

/**
 * Captures the shared baseline once [ready] is true. Add screens pass `ready = true` (captures on
 * first composition, when the fields are empty); Edit screens pass `ready = prefilled` (captures
 * after the prefill has fully seeded [shared], including the counterparty). Null until ready.
 */
@Composable
fun rememberSharedBaseline(shared: TransactionFormState, ready: Boolean): SharedBaseline? =
    remember(ready) { if (ready) SharedBaseline.from(shared) else null }

/**
 * Centralizes the [BackHandler] and the Save / Discard / Cancel dialog for the add/edit screens.
 * Returns the `onBack` lambda to also wire onto the TopAppBar back button, so the arrow and
 * system/gesture back behave identically. When the form is not dirty, navigates immediately.
 */
@Composable
fun rememberUnsavedChangesBackGuard(
    gate: FormBackGate,
    onNavigateBack: () -> Unit
): () -> Unit {
    var showDialog by remember { mutableStateOf(false) }
    val onBack = { if (gate.isDirty()) showDialog = true else onNavigateBack() }
    BackHandler { onBack() }
    if (showDialog) {
        UnsavedChangesDialog(
            onSave = { showDialog = false; gate.requestSave() },
            onDiscard = { showDialog = false; onNavigateBack() },
            onCancel = { showDialog = false }
        )
    }
    return onBack
}

@Composable
internal fun UnsavedChangesDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        // Tapping outside or dialog-back is treated as Cancel so changes are never lost implicitly.
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.discard_changes_title)) },
        text = { Text(stringResource(R.string.discard_changes_message)) },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.action_save)) } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.action_discard)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}
