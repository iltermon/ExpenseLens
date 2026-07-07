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
import com.iltermon.expenselens.data.Category

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(viewModel: ExpenseLensViewModel, onNavigateBack: () -> Unit) {
    val categories by viewModel.allCategories.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    var editCategory by remember { mutableStateOf<Category?>(null) }
    var deleteCategory by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_categories)) },
                navigationIcon = { BackButton(onClick = onNavigateBack) },
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.category_add_title))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(categories) { category ->
                CategoryManageRow(
                    category = category,
                    onEdit = { editCategory = category },
                    onToggleActive = { viewModel.toggleCategoryActive(category) },
                    onDelete = { deleteCategory = category }
                )
                HorizontalDivider()
            }
        }
    }

    if (showAdd) {
        CategoryDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onConfirm = { category -> viewModel.insertCategory(category); showAdd = false }
        )
    }

    editCategory?.let { category ->
        CategoryDialog(
            initial = category,
            onDismiss = { editCategory = null },
            onConfirm = { updated -> viewModel.updateCategory(category, updated); editCategory = null }
        )
    }

    deleteCategory?.let { category ->
        DeleteWithReassignDialog(
            title = stringResource(R.string.delete_category_title, category.name),
            message = stringResource(R.string.delete_category_message),
            targets = categories.filter { it.id != category.id },
            targetLabel = { it.name },
            allowLeaveUnassigned = false,
            onDismiss = { deleteCategory = null },
            onConfirm = { target -> target?.let { viewModel.deleteCategory(category, it) }; deleteCategory = null }
        )
    }
}

@Composable
private fun CategoryManageRow(
    category: Category,
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
                .graphicsLayer { alpha = if (category.active) 1f else 0.45f }
        ) {
            Text(text = category.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(text = categoryTypeLabel(category.type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LimitSubtitle(category.limitMonthly, category.limitYearly)
            if (!category.active) DisabledBadge()
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_manage_category, category.name))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit)) },
                    onClick = { menuOpen = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(if (category.active) R.string.action_disable else R.string.action_enable)) },
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
private fun CategoryDialog(initial: Category?, onDismiss: () -> Unit, onConfirm: (Category) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type) }
    var typeExpanded by remember { mutableStateOf(false) }
    var monthly by remember { mutableStateOf(initial?.limitMonthly?.let { "%.2f".format(it) } ?: "") }
    var yearly by remember { mutableStateOf(initial?.limitYearly?.let { "%.2f".format(it) } ?: "") }
    val currencySymbol = LocalCurrencySymbol.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.category_add_title else R.string.category_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.category_name_field)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = !typeExpanded }) {
                    OutlinedTextField(
                        value = categoryTypeLabel(type),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.category_appears_in)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.category_type_both)) }, onClick = { type = null; typeExpanded = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.category_type_expense_only)) }, onClick = { type = "expense"; typeExpanded = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.category_type_income_only)) }, onClick = { type = "income"; typeExpanded = false })
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
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            (initial ?: Category(name = "", type = type)).copy(
                                name = name.trim(),
                                type = type,
                                limitMonthly = monthly.trim().toDoubleOrNull(),
                                limitYearly = yearly.trim().toDoubleOrNull()
                            )
                        )
                    }
                },
                enabled = name.isNotBlank()
            ) { Text(stringResource(if (initial == null) R.string.action_add else R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
