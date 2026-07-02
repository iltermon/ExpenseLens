package com.iltermon.expenselens.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iltermon.expenselens.R
import com.iltermon.expenselens.data.Account
import com.iltermon.expenselens.data.Category
import java.util.Locale

private val accountTypes = listOf("Debit", "Credit Card", "Investment", "Cash", "Savings")

// Currency symbols the user can pick (display only). Paired with their label resource.
private val currencyOptions = listOf(
    "€" to R.string.currency_euro,
    "₺" to R.string.currency_lira,
    "$" to R.string.currency_dollar,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ExpenseLensViewModel, onChangeLanguage: (String) -> Unit) {
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var editLimitAccount by remember { mutableStateOf<Account?>(null) }
    var editLimitCategory by remember { mutableStateOf<Category?>(null) }

    val context = LocalContext.current
    val importStatus by viewModel.importStatus.collectAsState()
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importFromUri(context, it) } }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding)
        ) {
            item {
                PreferencesSection(
                    currencySymbol = currencySymbol,
                    languageTag = LocaleManager.getLanguageTag(context),
                    onCurrencySelected = { viewModel.setCurrencySymbol(it) },
                    onLanguageSelected = onChangeLanguage
                )
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))
            }
            item {
                SectionHeader(title = stringResource(R.string.settings_accounts), onAdd = { showAddAccountDialog = true })
                HorizontalDivider()
            }
            items(accounts) { account ->
                AccountRow(
                    account = account,
                    onEditLimit = { editLimitAccount = account },
                    onDelete = { viewModel.deleteAccount(account) }
                )
                HorizontalDivider()
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
            item {
                SectionHeader(title = stringResource(R.string.settings_categories), onAdd = { showAddCategoryDialog = true })
                HorizontalDivider()
            }
            items(categories) { category ->
                CategoryRow(
                    category = category,
                    onEditLimit = { editLimitCategory = category },
                    onDelete = { viewModel.deleteCategory(category) }
                )
                HorizontalDivider()
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
            item {
                Text(
                    text = stringResource(R.string.settings_advanced),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                HorizontalDivider()
            }
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Text(stringResource(R.string.settings_import_excel))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showClearDataDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(stringResource(R.string.settings_clear_transactions))
                    }
                    importStatus?.let { status ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = importStatusText(status), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (showAddAccountDialog) {
        AddAccountDialog(
            onDismiss = { showAddAccountDialog = false },
            onConfirm = { account ->
                viewModel.insertAccount(account)
                showAddAccountDialog = false
            }
        )
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { category ->
                viewModel.insertCategory(category)
                showAddCategoryDialog = false
            }
        )
    }

    editLimitAccount?.let { account ->
        LimitsDialog(
            title = stringResource(R.string.limit_dialog_title, account.name),
            currentMonthly = account.limitMonthly,
            currentYearly = account.limitYearly,
            onDismiss = { editLimitAccount = null },
            onConfirm = { monthly, yearly ->
                viewModel.insertAccount(account.copy(limitMonthly = monthly, limitYearly = yearly))
                editLimitAccount = null
            }
        )
    }

    editLimitCategory?.let { category ->
        LimitsDialog(
            title = stringResource(R.string.limit_dialog_title, category.name),
            currentMonthly = category.limitMonthly,
            currentYearly = category.limitYearly,
            onDismiss = { editLimitCategory = null },
            onConfirm = { monthly, yearly ->
                viewModel.insertCategory(category.copy(limitMonthly = monthly, limitYearly = yearly))
                editLimitCategory = null
            }
        )
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text(stringResource(R.string.settings_clear_dialog_title)) },
            text = { Text(stringResource(R.string.settings_clear_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearTransactions()
                        showClearDataDialog = false
                    }
                ) { Text(stringResource(R.string.action_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

}

/** Renders an [ImportStatus] in the user's language. */
@Composable
private fun importStatusText(status: ImportStatus): String = when (status) {
    ImportStatus.Importing -> stringResource(R.string.import_importing)
    is ImportStatus.Imported -> stringResource(
        R.string.import_done,
        status.expenses, status.income, status.templates, status.accounts, status.categories
    )
    is ImportStatus.ImportFailed -> stringResource(R.string.import_failed, status.message ?: "")
    ImportStatus.Clearing -> stringResource(R.string.clear_clearing)
    ImportStatus.Cleared -> stringResource(R.string.clear_done)
    is ImportStatus.ClearFailed -> stringResource(R.string.clear_failed, status.message ?: "")
}

/** Language + currency pickers. Language uses the per-app locale API; currency is display only. */
@Composable
private fun PreferencesSection(
    currencySymbol: String,
    languageTag: String,
    onCurrencySelected: (String) -> Unit,
    onLanguageSelected: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Resolve the language shown: an explicit choice wins, otherwise the device default
        // (Turkish device → Turkish, everything else → English).
        val currentLang = when {
            languageTag.isNotBlank() -> languageTag
            Locale.getDefault().language == "tr" -> "tr"
            else -> "en"
        }

        SettingDropdown(
            label = stringResource(R.string.settings_language),
            options = listOf("en", "tr"),
            selected = currentLang,
            optionLabel = { code ->
                if (code == "tr") stringResource(R.string.language_turkish) else stringResource(R.string.language_english)
            },
            onSelect = onLanguageSelected
        )

        SettingDropdown(
            label = stringResource(R.string.settings_currency),
            options = currencyOptions.map { it.first },
            selected = currencySymbol,
            optionLabel = { symbol ->
                val res = currencyOptions.firstOrNull { it.first == symbol }?.second ?: R.string.currency_euro
                stringResource(res)
            },
            onSelect = onCurrencySelected
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingDropdown(
    label: String,
    options: List<String>,
    selected: String,
    optionLabel: @Composable (String) -> String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_section, title))
        }
    }
}

@Composable
private fun AccountRow(account: Account, onEditLimit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = account.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(text = accountTypeLabel(account.type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LimitSubtitle(account.limitMonthly, account.limitYearly)
        }
        IconButton(onClick = onEditLimit) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_set_account_limit))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete_account))
        }
    }
}

@Composable
private fun CategoryRow(category: Category, onEditLimit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = category.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(text = categoryTypeLabel(category.type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LimitSubtitle(category.limitMonthly, category.limitYearly)
        }
        IconButton(onClick = onEditLimit) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_set_category_limit))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete_category))
        }
    }
}

/** Maps the stored category type (null/"expense"/"income") to its localized label. */
@Composable
private fun categoryTypeLabel(type: String?): String = when (type) {
    "expense" -> stringResource(R.string.category_type_expense_only)
    "income" -> stringResource(R.string.category_type_income_only)
    else -> stringResource(R.string.category_type_both)
}

@Composable
private fun LimitSubtitle(limitMonthly: Double?, limitYearly: Double?) {
    if (limitMonthly == null && limitYearly == null) return
    val parts = mutableListOf<String>()
    if (limitMonthly != null) parts.add(stringResource(R.string.limit_per_month, money(limitMonthly, 0)))
    if (limitYearly != null) parts.add(stringResource(R.string.limit_per_year, money(limitYearly, 0)))
    Text(
        text = stringResource(R.string.limit_subtitle, parts.joinToString(stringResource(R.string.limit_separator))),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LimitsDialog(
    title: String,
    currentMonthly: Double?,
    currentYearly: Double?,
    onDismiss: () -> Unit,
    onConfirm: (monthly: Double?, yearly: Double?) -> Unit
) {
    var monthly by remember { mutableStateOf(currentMonthly?.let { "%.2f".format(it) } ?: "") }
    var yearly by remember { mutableStateOf(currentYearly?.let { "%.2f".format(it) } ?: "") }
    val currencySymbol = LocalCurrencySymbol.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Text(
                    stringResource(R.string.limit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(monthly.trim().toDoubleOrNull(), yearly.trim().toDoubleOrNull())
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccountDialog(onDismiss: () -> Unit, onConfirm: (Account) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(accountTypes.first()) }
    var typeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.account_name_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded }
                ) {
                    OutlinedTextField(
                        value = accountTypeLabel(type),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.field_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        accountTypes.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(accountTypeLabel(option)) },
                                onClick = { type = option; typeExpanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(Account(name = name.trim(), type = type)) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCategoryDialog(onDismiss: () -> Unit, onConfirm: (Category) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf<String?>(null) }
    var typeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.category_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.category_name_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded }
                ) {
                    OutlinedTextField(
                        value = categoryTypeLabel(type),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.category_appears_in)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.category_type_both)) }, onClick = { type = null; typeExpanded = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.category_type_expense_only)) }, onClick = { type = "expense"; typeExpanded = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.category_type_income_only)) }, onClick = { type = "income"; typeExpanded = false })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(Category(name = name.trim(), type = type)) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
