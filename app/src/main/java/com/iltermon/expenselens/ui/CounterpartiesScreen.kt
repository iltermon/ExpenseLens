package com.iltermon.expenselens.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.iltermon.expenselens.R
import com.iltermon.expenselens.data.Account
import com.iltermon.expenselens.data.Category
import com.iltermon.expenselens.data.Counterparty

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterpartiesScreen(viewModel: ExpenseLensViewModel, onNavigateBack: () -> Unit) {
    val counterparties by viewModel.counterparties.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    var editCounterparty by remember { mutableStateOf<Counterparty?>(null) }
    var mergeCounterparty by remember { mutableStateOf<Counterparty?>(null) }
    var deleteCounterparty by remember { mutableStateOf<Counterparty?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_counterparties)) },
                navigationIcon = { BackButton(onClick = onNavigateBack) },
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.counterparty_add_title))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(counterparties) { counterparty ->
                CounterpartyRow(
                    counterparty = counterparty,
                    categories = categories,
                    accounts = accounts,
                    onEdit = { editCounterparty = counterparty },
                    onMerge = { mergeCounterparty = counterparty },
                    onDelete = { deleteCounterparty = counterparty }
                )
                HorizontalDivider()
            }
        }
    }

    if (showAdd) {
        CounterpartyDialog(
            initial = null,
            categories = categories,
            accounts = accounts,
            onDismiss = { showAdd = false },
            onConfirm = { counterparty -> viewModel.insertCounterparty(counterparty); showAdd = false }
        )
    }

    editCounterparty?.let { counterparty ->
        CounterpartyDialog(
            initial = counterparty,
            categories = categories,
            accounts = accounts,
            onDismiss = { editCounterparty = null },
            onConfirm = { updated -> viewModel.updateCounterparty(updated); editCounterparty = null }
        )
    }

    mergeCounterparty?.let { source ->
        MergeCounterpartyDialog(
            source = source,
            others = counterparties.filter { it.id != source.id },
            onDismiss = { mergeCounterparty = null },
            onConfirm = { target -> viewModel.mergeCounterparties(source, target); mergeCounterparty = null }
        )
    }

    deleteCounterparty?.let { counterparty ->
        DeleteWithReassignDialog(
            title = stringResource(R.string.delete_counterparty_title, counterparty.name),
            message = stringResource(R.string.delete_counterparty_message),
            targets = counterparties.filter { it.id != counterparty.id },
            targetLabel = { it.name },
            allowLeaveUnassigned = true,
            onDismiss = { deleteCounterparty = null },
            onConfirm = { target -> viewModel.deleteCounterparty(counterparty, target); deleteCounterparty = null }
        )
    }
}

@Composable
private fun CounterpartyRow(
    counterparty: Counterparty,
    categories: List<Category>,
    accounts: List<Account>,
    onEdit: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val subtitle = buildList {
        counterparty.defaultCategoryId?.let { id -> categories.find { it.id == id }?.let { add(it.name) } }
        counterparty.defaultAccountId?.let { id -> accounts.find { it.id == id }?.let { add(it.name) } }
    }.joinToString(stringResource(R.string.limit_separator))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = counterparty.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_edit_counterparty))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.cd_edit_counterparty)) },
                    onClick = { menuOpen = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.cd_merge_counterparty)) },
                    onClick = { menuOpen = false; onMerge() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CounterpartyDialog(
    initial: Counterparty?,
    categories: List<Category>,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirm: (Counterparty) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var defaultCategoryId by remember { mutableStateOf(initial?.defaultCategoryId) }
    var defaultAccountId by remember { mutableStateOf(initial?.defaultAccountId) }
    var catExpanded by remember { mutableStateOf(false) }
    var accExpanded by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    val noneLabel = stringResource(R.string.action_none)

    fun save() {
        if (name.isNotBlank()) {
            onConfirm(
                (initial ?: Counterparty(name = "")).copy(
                    name = name.trim(),
                    defaultCategoryId = defaultCategoryId,
                    defaultAccountId = defaultAccountId
                )
            )
        }
    }

    val isDirty = name != (initial?.name ?: "") ||
        defaultCategoryId != initial?.defaultCategoryId ||
        defaultAccountId != initial?.defaultAccountId

    AlertDialog(
        onDismissRequest = { if (isDirty) showDiscard = true else onDismiss() },
        title = { Text(stringResource(if (initial == null) R.string.counterparty_add_title else R.string.counterparty_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.counterparty_name_field)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = !catExpanded }) {
                    OutlinedTextField(
                        value = categories.find { it.id == defaultCategoryId }?.name ?: noneLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.counterparty_default_category)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                        DropdownMenuItem(text = { Text(noneLabel) }, onClick = { defaultCategoryId = null; catExpanded = false })
                        categories.forEach { c ->
                            DropdownMenuItem(text = { Text(c.name) }, onClick = { defaultCategoryId = c.id; catExpanded = false })
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = accExpanded, onExpandedChange = { accExpanded = !accExpanded }) {
                    OutlinedTextField(
                        value = accounts.find { it.id == defaultAccountId }?.let { accountWithType(it.name, it.type) } ?: noneLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.counterparty_default_account)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = accExpanded, onDismissRequest = { accExpanded = false }) {
                        DropdownMenuItem(text = { Text(noneLabel) }, onClick = { defaultAccountId = null; accExpanded = false })
                        accounts.forEach { a ->
                            DropdownMenuItem(text = { Text(accountWithType(a.name, a.type)) }, onClick = { defaultAccountId = a.id; accExpanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { save() },
                enabled = name.isNotBlank()
            ) { Text(stringResource(if (initial == null) R.string.action_add else R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )

    if (showDiscard) {
        UnsavedChangesDialog(
            onSave = { showDiscard = false; save() },
            onDiscard = { showDiscard = false; onDismiss() },
            onCancel = { showDiscard = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MergeCounterpartyDialog(
    source: Counterparty,
    others: List<Counterparty>,
    onDismiss: () -> Unit,
    onConfirm: (Counterparty) -> Unit
) {
    var target by remember { mutableStateOf<Counterparty?>(null) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.counterparty_merge_title)) },
        text = {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = target?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(source.name) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    others.forEach { cp ->
                        DropdownMenuItem(text = { Text(cp.name) }, onClick = { target = cp; expanded = false })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { target?.let(onConfirm) }, enabled = target != null) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
