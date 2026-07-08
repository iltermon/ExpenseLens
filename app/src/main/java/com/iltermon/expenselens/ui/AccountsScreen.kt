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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iltermon.expenselens.R
import com.iltermon.expenselens.data.Account

private val accountTypes = listOf("Debit", "Credit Card", "Investment", "Cash", "Savings")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(viewModel: ExpenseLensViewModel, onNavigateBack: () -> Unit) {
    val accounts by viewModel.accounts.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    var editAccount by remember { mutableStateOf<Account?>(null) }
    var deleteAccount by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_accounts)) },
                navigationIcon = { BackButton(onClick = onNavigateBack) },
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.account_add_title))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(accounts) { account ->
                AccountManageRow(
                    account = account,
                    onEdit = { editAccount = account },
                    onToggleActive = { viewModel.toggleAccountActive(account) },
                    onDelete = { deleteAccount = account }
                )
                HorizontalDivider()
            }
        }
    }

    if (showAdd) {
        AccountDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onConfirm = { account -> viewModel.insertAccount(account); showAdd = false }
        )
    }

    editAccount?.let { account ->
        AccountDialog(
            initial = account,
            onDismiss = { editAccount = null },
            onConfirm = { updated -> viewModel.updateAccount(updated); editAccount = null }
        )
    }

    deleteAccount?.let { account ->
        DeleteWithReassignDialog(
            title = stringResource(R.string.delete_account_title, account.name),
            message = stringResource(R.string.delete_account_message),
            targets = accounts.filter { it.id != account.id },
            targetLabel = { accountWithType(it.name, it.type) },
            allowLeaveUnassigned = true,
            onDismiss = { deleteAccount = null },
            onConfirm = { target -> viewModel.deleteAccount(account, target); deleteAccount = null }
        )
    }
}

@Composable
private fun AccountManageRow(
    account: Account,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                // Grey out a disabled account so its inactive state reads at a glance.
                .graphicsLayer { alpha = if (account.active) 1f else 0.45f }
        ) {
            Text(text = account.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(text = accountTypeLabel(account.type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LimitSubtitle(account.limitMonthly, account.limitYearly)
            if (!account.active) DisabledBadge()
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_manage_account, account.name))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit)) },
                    onClick = { menuOpen = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(if (account.active) R.string.action_disable else R.string.action_enable)) },
                    onClick = { menuOpen = false; onToggleActive() }
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
private fun AccountDialog(initial: Account?, onDismiss: () -> Unit, onConfirm: (Account) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: accountTypes.first()) }
    var typeExpanded by remember { mutableStateOf(false) }
    var monthly by remember { mutableStateOf(initial?.limitMonthly?.let { "%.2f".format(it) } ?: "") }
    var yearly by remember { mutableStateOf(initial?.limitYearly?.let { "%.2f".format(it) } ?: "") }
    var showDiscard by remember { mutableStateOf(false) }
    val currencySymbol = LocalCurrencySymbol.current

    fun save() {
        if (name.isNotBlank()) {
            onConfirm(
                (initial ?: Account(name = "", type = type)).copy(
                    name = name.trim(),
                    type = type,
                    limitMonthly = monthly.trim().toDoubleOrNull(),
                    limitYearly = yearly.trim().toDoubleOrNull()
                )
            )
        }
    }

    val isDirty = name != (initial?.name ?: "") ||
        type != (initial?.type ?: accountTypes.first()) ||
        monthly != (initial?.limitMonthly?.let { "%.2f".format(it) } ?: "") ||
        yearly != (initial?.limitYearly?.let { "%.2f".format(it) } ?: "")

    AlertDialog(
        onDismissRequest = { if (isDirty) showDiscard = true else onDismiss() },
        title = { Text(stringResource(if (initial == null) R.string.account_add_title else R.string.account_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.account_name_field)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = !typeExpanded }) {
                    OutlinedTextField(
                        value = accountTypeLabel(type),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.field_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        accountTypes.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(accountTypeLabel(option)) },
                                onClick = { type = option; typeExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = monthly,
                    onValueChange = { monthly = it },
                    label = { Text(stringResource(R.string.limit_monthly_field, currencySymbol)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = yearly,
                    onValueChange = { yearly = it },
                    label = { Text(stringResource(R.string.limit_yearly_field, currencySymbol)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
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
