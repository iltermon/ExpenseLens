package com.iltermon.expenselens.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iltermon.expenselens.R

/**
 * Confirm-delete dialog that also decides where the deleted item's transactions go. Generic over the
 * replacement type [T] (Account / Category / Counterparty).
 *
 * When [allowLeaveUnassigned] is true (nullable references — accounts, categories, counterparties)
 * the picker offers a "leave unassigned" choice and defaults to it, so confirm is immediately
 * enabled. When false, a target must be chosen before confirm enables. [onConfirm] passes the
 * chosen target, or null for "leave unassigned".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DeleteWithReassignDialog(
    title: String,
    message: String,
    targets: List<T>,
    targetLabel: @Composable (T) -> String,
    allowLeaveUnassigned: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (target: T?) -> Unit
) {
    var selected by remember { mutableStateOf<T?>(null) }
    // Accounts/counterparties start on a valid "leave unassigned" choice; a category has no valid
    // choice until the user picks a target.
    var chosen by remember { mutableStateOf(allowLeaveUnassigned) }
    var expanded by remember { mutableStateOf(false) }

    val leaveLabel = stringResource(R.string.delete_leave_unassigned)
    val fieldValue = when {
        selected != null -> targetLabel(selected as T)
        chosen && allowLeaveUnassigned -> leaveLabel
        else -> ""
    }
    val confirmEnabled = chosen && (allowLeaveUnassigned || selected != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = fieldValue,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.delete_move_to_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        if (allowLeaveUnassigned) {
                            DropdownMenuItem(
                                text = { Text(leaveLabel) },
                                onClick = { selected = null; chosen = true; expanded = false }
                            )
                        }
                        targets.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(targetLabel(t)) },
                                onClick = { selected = t; chosen = true; expanded = false }
                            )
                        }
                    }
                }
                if (!allowLeaveUnassigned) {
                    Text(
                        stringResource(R.string.delete_reassign_required_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }, enabled = confirmEnabled) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
