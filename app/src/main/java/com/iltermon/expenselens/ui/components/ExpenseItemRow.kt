package com.iltermon.expenselens.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.iltermon.expenselens.R
import com.iltermon.expenselens.data.RecurringTransactionTemplate
import com.iltermon.expenselens.data.occurrencesInRange
import com.iltermon.expenselens.ui.ExpenseItem
import com.iltermon.expenselens.ui.transactions.ExpenseItemCard
import com.iltermon.expenselens.ui.ExpenseLensViewModel
import java.time.LocalDate

/**
 * One list row: swipe-to-reveal delete plus tap-to-edit, with the recurring-vs-one-time routing
 * applied in one place so Expenses and Income behave identically.
 *
 * A row is treated as part of a recurring **series** when its [com.iltermon.expenselens.ui.ExpenseItem.templateId] resolves to
 * an existing template — this covers both projected occurrences and auto-generated paid rows.
 * Deleting then acts on the whole series. Tapping a recorded payment of a series asks whether to
 * edit just that payment or the whole series; tapping a projected occurrence edits its template
 * (it has no row of its own yet). Genuine one-time rows (and orphans whose template was already
 * deleted) act on the single transaction.
 *
 * Deleting is for fixing mistakes: a series delete wipes the template and every transaction it
 * generated. To merely stop a series going forward, edit it and set an end date instead.
 */
@Composable
fun ExpenseItemRow(
    item: ExpenseItem,
    templates: List<RecurringTransactionTemplate>,
    viewModel: ExpenseLensViewModel,
    onEditTransaction: (Int) -> Unit,
    onEditTemplate: (Int) -> Unit,
    counterpartyNames: Map<Int, String> = emptyMap(),
    categoryNames: Map<Int, String> = emptyMap()
) {
    val template = item.templateId?.let { tid -> templates.find { it.id == tid } }
    var showDelete by remember { mutableStateOf(false) }
    var showEditChoice by remember { mutableStateOf(false) }

    // Occurrences left in a finite series, counting this row's occurrence through the end date
    // (inclusive). Open-ended templates have no finite count, so nothing is shown.
    val remainingOccurrences = template?.endDate?.let { end ->
        template.occurrencesInRange(LocalDate.parse(item.date), LocalDate.parse(end)).size
            .takeIf { it > 0 }
    }

    SwipeToRevealRow(onDelete = { showDelete = true }) {
        ExpenseItemCard(
            item = item,
            onTogglePaid = { viewModel.togglePaid(it) },
            onClick = {
                when {
                    item.transactionId != null && template != null -> showEditChoice = true
                    item.transactionId != null -> onEditTransaction(item.transactionId)
                    template != null -> onEditTemplate(template.id)
                }
            },
            counterpartyName = item.counterpartyId?.let { counterpartyNames[it] },
            categoryName = item.categoryId?.let { categoryNames[it] },
            remainingOccurrences = remainingOccurrences
        )
    }

    if (showEditChoice && template != null && item.transactionId != null) {
        AlertDialog(
            onDismissRequest = { showEditChoice = false },
            title = { Text(stringResource(R.string.edit_choice_title, item.description)) },
            text = { Text(stringResource(R.string.edit_choice_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showEditChoice = false
                    onEditTemplate(template.id)
                }) { Text(stringResource(R.string.edit_choice_whole_series)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showEditChoice = false
                    onEditTransaction(item.transactionId)
                }) { Text(stringResource(R.string.edit_choice_this_payment)) }
            }
        )
    }

    if (showDelete) {
        if (template != null) {
            AlertDialog(
                onDismissRequest = { showDelete = false },
                title = { Text(stringResource(R.string.delete_recurring_title, item.description)) },
                text = { Text(stringResource(R.string.delete_recurring_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteTemplate(template)
                        showDelete = false
                    }) { Text(stringResource(R.string.action_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.action_cancel)) }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showDelete = false },
                title = { Text(stringResource(R.string.delete_transaction_title)) },
                text = { Text(stringResource(R.string.delete_transaction_message, item.description)) },
                confirmButton = {
                    TextButton(onClick = {
                        item.transactionId?.let { viewModel.deleteTransactionById(it) }
                        showDelete = false
                    }) { Text(stringResource(R.string.action_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.action_cancel)) }
                }
            )
        }
    }
}
