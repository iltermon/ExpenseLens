package com.iltermon.expenselens.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iltermon.expenselens.data.Account
import com.iltermon.expenselens.data.Category
import com.iltermon.expenselens.data.RecurringTemplate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val frequencyUnits = listOf("Day", "Week", "Month", "Year")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecurringForm(
    categories: List<Category>,
    accounts: List<Account>,
    isExpense: Boolean,
    onSave: (RecurringTemplate) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var selectedAccount by remember { mutableStateOf<Account?>(null) }
    var accountExpanded by remember { mutableStateOf(false) }

    var frequencyInterval by remember { mutableStateOf("1") }
    var frequencyUnit by remember { mutableStateOf(frequencyUnits[2]) } // Monthly default
    var unitExpanded by remember { mutableStateOf(false) }

    val now = LocalDate.now()
    var startDate by remember { mutableStateOf(now) }
    var isFinite by remember { mutableStateOf(false) }
    var endDate by remember { mutableStateOf(now.plusMonths(1)) }

    val intervalInt = frequencyInterval.toIntOrNull()?.coerceAtLeast(1) ?: 1
    var autoPayment by remember { mutableStateOf(isExpense) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount (€)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        ExposedDropdownMenuBox(
            expanded = categoryExpanded,
            onExpandedChange = { categoryExpanded = !categoryExpanded }
        ) {
            OutlinedTextField(
                value = selectedCategory?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                categories.forEach { cat ->
                    DropdownMenuItem(text = { Text(cat.name) }, onClick = { selectedCategory = cat; categoryExpanded = false })
                }
            }
        }
        ExposedDropdownMenuBox(
            expanded = accountExpanded,
            onExpandedChange = { accountExpanded = !accountExpanded }
        ) {
            OutlinedTextField(
                value = selectedAccount?.let { "${it.name} (${it.type})" } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Account (optional)") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) {
                DropdownMenuItem(text = { Text("None") }, onClick = { selectedAccount = null; accountExpanded = false })
                accounts.forEach { acc ->
                    DropdownMenuItem(
                        text = { Text("${acc.name} (${acc.type})") },
                        onClick = { selectedAccount = acc; accountExpanded = false }
                    )
                }
            }
        }
        Text(
            text = "Recurrence",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = frequencyInterval,
                onValueChange = { if (it.length <= 3) frequencyInterval = it.filter { c -> c.isDigit() } },
                label = { Text("Frequency") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(100.dp)
            )
            ExposedDropdownMenuBox(
                expanded = unitExpanded,
                onExpandedChange = { unitExpanded = !unitExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = frequencyUnit,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Unit") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                    frequencyUnits.forEach { unit ->
                        DropdownMenuItem(text = { Text(unit) }, onClick = { frequencyUnit = unit; unitExpanded = false })
                    }
                }
            }
        }

        DatePickerField(label = "Start date", value = startDate, onValueChange = { startDate = it })

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Fixed end date", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = isFinite, onCheckedChange = { isFinite = it })
        }

        if (isFinite) {
            DatePickerField(label = "End date", value = endDate, onValueChange = { endDate = it })
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Auto payment", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = autoPayment, onCheckedChange = { autoPayment = it })
        }

        Button(
            onClick = {
                val parsedAmount = amount.toDoubleOrNull() ?: return@Button
                val categoryName = selectedCategory?.name ?: return@Button
                if (description.isBlank()) return@Button
                onSave(
                    RecurringTemplate(
                        description = description,
                        amount = parsedAmount,
                        category = categoryName,
                        startDate = startDate.toString(),
                        endDate = if (isFinite) endDate.toString() else null,
                        isExpense = isExpense,
                        frequencyInterval = intervalInt,
                        frequencyUnit = frequencyUnit,
                        autoPayment = autoPayment,
                        accountId = selectedAccount?.id
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isExpense) "Save Recurring Expense" else "Save Recurring Income")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerField(label: String, value: LocalDate, onValueChange: (LocalDate) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )

    Box {
        OutlinedTextField(
            value = value.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(modifier = Modifier.matchParentSize().clickable { showDialog = true })
    }

    if (showDialog) {
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onValueChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
