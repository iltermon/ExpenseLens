package com.iltermon.expenselens.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.iltermon.expenselens.R
import com.iltermon.expenselens.data.Counterparty
import kotlin.math.min

/**
 * The Store/vendor/payer field shared by the one-time and recurring forms. Title-cases each word as
 * the user types and offers a picker of existing counterparties: substring matches first, then
 * near-matches under a "did you mean?" hint (to discourage duplicates like "Aldi" vs "Aldi Nord"),
 * then an "Add …" entry when the typed name is new. Selecting an existing one hands it back so the
 * caller can prefill category/account from its defaults.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CounterpartyField(
    value: String,
    onValueChange: (String) -> Unit,
    counterparties: List<Counterparty>,
    onCounterpartySelected: (Counterparty) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }

    val query = value.trim()
    val queryLower = query.lowercase()
    val exactMatch = counterparties.any { it.name.equals(query, ignoreCase = true) }
    val contains =
        if (query.isBlank()) emptyList()
        else counterparties.filter { it.name.contains(query, ignoreCase = true) }
    // Only bother with fuzzy matching once there's enough typed to be meaningful.
    val near =
        if (query.length < 3) emptyList()
        else counterparties.filter { it !in contains && levenshtein(it.name.lowercase(), queryLower) <= 2 }
    val showAdd = query.isNotBlank() && !exactMatch
    val hasMenu = contains.isNotEmpty() || near.isNotEmpty() || showAdd

    ExposedDropdownMenuBox(
        expanded = expanded && hasMenu,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(stringResource(R.string.form_counterparty)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            isError = isError,
            supportingText = { if (isError) Text(stringResource(R.string.field_required)) },
            modifier = modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded && hasMenu, onDismissRequest = { expanded = false }) {
            contains.forEach { cp ->
                DropdownMenuItem(text = { Text(cp.name) }, onClick = {
                    onCounterpartySelected(cp)
                    expanded = false
                })
            }
            if (near.isNotEmpty()) {
                DropdownMenuItem(
                    enabled = false,
                    text = {
                        Text(
                            stringResource(R.string.counterparty_did_you_mean),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {}
                )
                near.forEach { cp ->
                    DropdownMenuItem(text = { Text(cp.name) }, onClick = {
                        onCounterpartySelected(cp)
                        expanded = false
                    })
                }
            }
            if (showAdd) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.counterparty_add_new, query)) },
                    onClick = {
                        onValueChange(query)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Outcome of matching the typed name against known counterparties at save time. Either the choice
 * is ready to persist, or an existing counterparty's defaults differ from the form and the user
 * must first be asked whether to re-curate them.
 */
internal sealed interface CounterpartyResolution {
    data class Ready(val choice: CounterpartyChoice) : CounterpartyResolution
    data class NeedsPrompt(val existing: Counterparty) : CounterpartyResolution
}

/** Resolves the counterparty field for a save; see [CounterpartyResolution]. */
internal fun counterpartyChoiceFor(
    name: String,
    counterparties: List<Counterparty>,
    category: String,
    accountId: Int?
): CounterpartyResolution {
    val trimmed = name.trim()
    val existing = counterparties.find { it.name.equals(trimmed, ignoreCase = true) }
        ?: return CounterpartyResolution.Ready(CounterpartyChoice.New(trimmed))
    val changed = existing.defaultCategory != category || existing.defaultAccountId != accountId
    return if (changed) CounterpartyResolution.NeedsPrompt(existing)
    else CounterpartyResolution.Ready(CounterpartyChoice.Existing(existing, updateDefaults = false))
}

/** Asks whether to update a known counterparty's default category/account. Dismiss = cancel save. */
@Composable
internal fun UpdateDefaultsDialog(name: String, onDecision: (Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.counterparty_update_defaults_title)) },
        text = { Text(stringResource(R.string.counterparty_update_defaults_message, name)) },
        confirmButton = { TextButton(onClick = { onDecision(true) }) { Text(stringResource(R.string.action_yes)) } },
        dismissButton = { TextButton(onClick = { onDecision(false) }) { Text(stringResource(R.string.action_no)) } }
    )
}

/** Classic Levenshtein edit distance, used only for short counterparty names. */
private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var prev = IntArray(b.length + 1) { it }
    var curr = IntArray(b.length + 1)
    for (i in 1..a.length) {
        curr[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
        }
        val tmp = prev; prev = curr; curr = tmp
    }
    return prev[b.length]
}
