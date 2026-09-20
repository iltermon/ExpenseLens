package com.iltermon.expenselens.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iltermon.expenselens.R
import com.iltermon.expenselens.data.Account
import com.iltermon.expenselens.data.Category
import com.iltermon.expenselens.data.Counterparty
import com.iltermon.expenselens.ui.transactions.ListFilterState
import com.iltermon.expenselens.ui.transactions.PaidStatusFilter
import com.iltermon.expenselens.ui.transactions.RecurrenceFilter
import com.iltermon.expenselens.ui.transactions.formatAmount
import com.iltermon.expenselens.ui.util.accountWithType
import com.iltermon.expenselens.ui.util.sanitizeAmountInput

/**
 * The advanced-filter [ModalBottomSheet]. Live-apply: every control mutates the ViewModel's filter
 * immediately via [onUpdateFilter], so the list behind the sheet updates as you go — no draft/apply
 * step. Categories, accounts and counterparties are OR-within, AND across; the "No account"/"No
 * counterparty" chips union with any selected ids.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FilterBottomSheet(
    filter: ListFilterState,
    categories: List<Category>,
    accounts: List<Account>,
    counterparties: List<Counterparty>,
    onUpdateFilter: ((ListFilterState) -> ListFilterState) -> Unit,
    onClearFilter: () -> Unit,
    onDismiss: () -> Unit,
    // Templates have no paid/one-time concept, so those sections can be hidden.
    showStatus: Boolean = true,
    showRecurrence: Boolean = true,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Sections scroll; the footer below stays pinned. No imePadding needed — the caller hides
            // the keyboard before opening, so the sheet measures at full height in one step.
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            if (categories.isNotEmpty()) {
                Section(stringResource(R.string.filter_section_category)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.forEach { c ->
                            val selected = c.id in filter.categoryIds
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    onUpdateFilter { f ->
                                        f.copy(categoryIds = if (selected) f.categoryIds - c.id else f.categoryIds + c.id)
                                    }
                                },
                                label = { Text(c.name) }
                            )
                        }
                    }
                }
            }

            Section(stringResource(R.string.filter_section_account)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { a ->
                        val selected = a.id in filter.accountIds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onUpdateFilter { f ->
                                    f.copy(accountIds = if (selected) f.accountIds - a.id else f.accountIds + a.id)
                                }
                            },
                            label = { Text(accountWithType(a.name, a.type)) }
                        )
                    }
                    FilterChip(
                        selected = filter.noAccount,
                        onClick = { onUpdateFilter { f -> f.copy(noAccount = !f.noAccount) } },
                        label = { Text(stringResource(R.string.filter_no_account)) }
                    )
                }
            }

            Section(stringResource(R.string.filter_section_counterparty)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    counterparties.forEach { cp ->
                        val selected = cp.id in filter.counterpartyIds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onUpdateFilter { f ->
                                    f.copy(counterpartyIds = if (selected) f.counterpartyIds - cp.id else f.counterpartyIds + cp.id)
                                }
                            },
                            label = { Text(cp.name) }
                        )
                    }
                    FilterChip(
                        selected = filter.noCounterparty,
                        onClick = { onUpdateFilter { f -> f.copy(noCounterparty = !f.noCounterparty) } },
                        label = { Text(stringResource(R.string.filter_no_counterparty)) }
                    )
                }
            }

            Section(stringResource(R.string.filter_section_amount)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Local text is the source of truth so typing "12." isn't reformatted mid-entry;
                    // a LaunchedEffect only re-seeds when an EXTERNAL change (e.g. Clear all) makes the
                    // filter disagree with what's typed, never in response to our own writes.
                    var minText by remember { mutableStateOf(filter.amountMin?.let { formatAmount(it) } ?: "") }
                    var maxText by remember { mutableStateOf(filter.amountMax?.let { formatAmount(it) } ?: "") }
                    LaunchedEffect(filter.amountMin) {
                        if (filter.amountMin != minText.toDoubleOrNull()) minText = filter.amountMin?.let {
                            formatAmount(
                                it
                            )
                        } ?: ""
                    }
                    LaunchedEffect(filter.amountMax) {
                        if (filter.amountMax != maxText.toDoubleOrNull()) maxText = filter.amountMax?.let {
                            formatAmount(
                                it
                            )
                        } ?: ""
                    }
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { raw ->
                            minText = sanitizeAmountInput(raw)
                            onUpdateFilter { f -> f.copy(amountMin = minText.toDoubleOrNull()) }
                        },
                        label = { Text(stringResource(R.string.filter_amount_min)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { raw ->
                            maxText = sanitizeAmountInput(raw)
                            onUpdateFilter { f -> f.copy(amountMax = maxText.toDoubleOrNull()) }
                        },
                        label = { Text(stringResource(R.string.filter_amount_max)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (showStatus) {
                Section(stringResource(R.string.filter_section_status)) {
                    val options = PaidStatusFilter.entries
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        options.forEachIndexed { index, status ->
                            SegmentedButton(
                                selected = filter.paidStatus == status,
                                onClick = { onUpdateFilter { f -> f.copy(paidStatus = status) } },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                            ) { Text(stringResource(paidStatusLabelRes(status))) }
                        }
                    }
                }
            }

            if (showRecurrence) {
                Section(stringResource(R.string.filter_section_recurrence)) {
                    val options = RecurrenceFilter.entries
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        options.forEachIndexed { index, rec ->
                            SegmentedButton(
                                selected = filter.recurrence == rec,
                                onClick = { onUpdateFilter { f -> f.copy(recurrence = rec) } },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                            ) { Text(stringResource(recurrenceLabelRes(rec))) }
                        }
                    }
                }
            }

            }
            // Anchored footer — Clear all / Done stay put while the sections above scroll.
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onClearFilter) { Text(stringResource(R.string.filter_clear_all)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.filter_done)) }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

/** Empty state shown when active filters (or a search) hide every row, as opposed to an empty period. */
@Composable
internal fun NoFilterResults(onClearFilters: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.filter_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClearFilters) { Text(stringResource(R.string.filter_clear_filters)) }
    }
}

internal fun paidStatusLabelRes(status: PaidStatusFilter): Int = when (status) {
    PaidStatusFilter.ALL -> R.string.filter_status_all
    PaidStatusFilter.PAID -> R.string.filter_status_paid
    PaidStatusFilter.UNPAID -> R.string.filter_status_unpaid
}

internal fun recurrenceLabelRes(recurrence: RecurrenceFilter): Int = when (recurrence) {
    RecurrenceFilter.ALL -> R.string.filter_recurrence_all
    RecurrenceFilter.RECURRING -> R.string.filter_recurrence_recurring
    RecurrenceFilter.ONE_TIME -> R.string.filter_recurrence_one_time
}
