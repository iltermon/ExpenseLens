package com.iltermon.expenselens.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iltermon.expenselens.R
import com.iltermon.expenselens.ui.ExpenseLensViewModel
import com.iltermon.expenselens.ui.ImportStatus
import com.iltermon.expenselens.ui.util.LocaleManager
import java.util.Locale

// Currency symbols the user can pick (display only). Paired with their label resource.
private val currencyOptions = listOf(
    "€" to R.string.currency_euro,
    "₺" to R.string.currency_lira,
    "$" to R.string.currency_dollar,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ExpenseLensViewModel,
    onChangeLanguage: (String) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenCounterparties: () -> Unit,
    onOpenTemplates: () -> Unit
) {
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    var showClearDataDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val importStatus by viewModel.importStatus.collectAsState()
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importFromUri(context, it) } }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                PreferencesSection(
                    currencySymbol = currencySymbol,
                    languageTag = LocaleManager.getLanguageTag(context),
                    onCurrencySelected = { viewModel.setCurrencySymbol(it) },
                    onLanguageSelected = onChangeLanguage
                )
                HorizontalDivider()
            }
            item { SettingsMenuRow(
                title = stringResource(R.string.settings_accounts),
                onClick = onOpenAccounts
            ); HorizontalDivider() }
            item { SettingsMenuRow(
                title = stringResource(R.string.settings_categories),
                onClick = onOpenCategories
            ); HorizontalDivider() }
            item { SettingsMenuRow(
                title = stringResource(R.string.settings_counterparties),
                onClick = onOpenCounterparties
            ); HorizontalDivider() }
            item { SettingsMenuRow(
                title = stringResource(R.string.settings_templates),
                onClick = onOpenTemplates
            ); HorizontalDivider() }

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

/** Renders an [com.iltermon.expenselens.ui.ImportStatus] in the user's language. */
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
