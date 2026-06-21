package com.iltermon.expenselens.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.iltermon.expenselens.data.RecurringTemplate

/**
 * One list row: swipe-to-reveal delete plus tap-to-edit, with the recurring-vs-one-time routing
 * applied in one place so Expenses and Income behave identically.
 *
 * A row is treated as part of a recurring **series** when its [ExpenseItem.templateId] resolves to
 * an existing template — this covers both projected occurrences and auto-generated paid rows.
 * Editing/deleting then acts on the template. Genuine one-time rows (and orphans whose template was
 * already deleted) act on the single transaction.
 *
 * Deleting is for fixing mistakes: a series delete wipes the template and every transaction it
 * generated. To merely stop a series going forward, edit it and set an end date instead.
 */
@Composable
fun ExpenseItemRow(
    item: ExpenseItem,
    templates: List<RecurringTemplate>,
    viewModel: ExpenseLensViewModel,
    onEditTransaction: (Int) -> Unit,
    onEditTemplate: (Int) -> Unit
) {
    val template = item.templateId?.let { tid -> templates.find { it.id == tid } }
    var showDelete by remember { mutableStateOf(false) }

    SwipeToRevealRow(onDelete = { showDelete = true }) {
        ExpenseItemCard(
            item = item,
            onTogglePaid = { viewModel.togglePaid(it) },
            onClick = {
                if (template != null) onEditTemplate(template.id)
                else item.transactionId?.let(onEditTransaction)
            }
        )
    }

    if (showDelete) {
        if (template != null) {
            AlertDialog(
                onDismissRequest = { showDelete = false },
                title = { Text("Delete recurring \"${item.description}\"?") },
                text = { Text("Removes the recurring payment and every entry it created. To stop it going forward instead, edit it and set an end date.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteTemplate(template)
                        showDelete = false
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { showDelete = false }) { Text("Cancel") }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showDelete = false },
                title = { Text("Delete transaction?") },
                text = { Text("\"${item.description}\" will be permanently removed.") },
                confirmButton = {
                    TextButton(onClick = {
                        item.transactionId?.let { viewModel.deleteTransactionById(it) }
                        showDelete = false
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { showDelete = false }) { Text("Cancel") }
                }
            )
        }
    }
}
