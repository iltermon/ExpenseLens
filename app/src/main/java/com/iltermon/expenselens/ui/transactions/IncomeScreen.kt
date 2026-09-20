package com.iltermon.expenselens.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iltermon.expenselens.R
import com.iltermon.expenselens.ui.ExpenseLensViewModel
import com.iltermon.expenselens.ui.components.TabScreenShell
import com.iltermon.expenselens.ui.components.ExpenseItemRow
import com.iltermon.expenselens.ui.components.FilterBottomSheet
import com.iltermon.expenselens.ui.components.NoFilterResults

@Composable
fun IncomeScreen(
    viewModel: ExpenseLensViewModel,
    onAddIncome: () -> Unit,
    onEditTransaction: (Int) -> Unit,
    onEditTemplate: (Int) -> Unit
) {
    val items by viewModel.incomeTabItems.collectAsState()
    val filter by viewModel.incomeFilter.collectAsState()
    val sort by viewModel.incomeSort.collectAsState()
    val templates by viewModel.allTemplates.collectAsState()
    val counterparties by viewModel.counterparties.collectAsState()
    val categories by viewModel.incomeCategories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val counterpartyNames = remember(counterparties) { counterparties.associate { it.id to it.name } }
    val categoryNames by viewModel.categoryNamesById.collectAsState()
    var searchActive by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }

    val recurringItems = items.filter { it.isRecurring && !it.isPaid }
    val pendingItems = items.filter { !it.isRecurring && !it.isPaid }
    val receivedItems = items.filter { it.isPaid }

    TabScreenShell(
        title = stringResource(R.string.nav_income),
        viewModel = viewModel,
        onAdd = onAddIncome,
        leftLabel = stringResource(R.string.income_this_month),
        leftAmount = items.sumOf { it.amount },
        rightLabel = stringResource(R.string.income_pending),
        rightAmount = (recurringItems + pendingItems).sumOf { it.amount },
        rightIsNegative = false,
        leftRecurring = items.filter { it.templateId != null }.sumOf { it.amount },
        rightRecurring = recurringItems.sumOf { it.amount },
        onSearchOpen = { viewModel.clearIncomeFilter(); searchActive = true },
        searchActive = searchActive,
        searchQuery = filter.searchQuery,
        onSearchQueryChange = { q -> viewModel.updateIncomeFilter { it.copy(searchQuery = q) } },
        onSearchClose = { viewModel.clearIncomeFilter(); searchActive = false },
        onOpenFilters = { showSheet = true },
        filterBadgeCount = filter.advancedCount,
        sort = sort,
        onSelectSort = viewModel::setIncomeSort
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when {
                items.isEmpty() -> item {
                    // Filters only exist inside search mode, so an active filter here means a
                    // search matched nothing; otherwise the period is genuinely empty.
                    if (filter.isActive) {
                        NoFilterResults(onClearFilters = viewModel::clearIncomeFilter)
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.no_income_for_period))
                        }
                    }
                }
                // A non-default sort flattens the sections into one ranked list.
                !sort.isDefault -> items(items) { item ->
                    ExpenseItemRow(
                        item,
                        templates,
                        viewModel,
                        onEditTransaction,
                        onEditTemplate,
                        counterpartyNames,
                        categoryNames
                    )
                }

                else -> {
                    if (recurringItems.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = stringResource(R.string.section_recurring),
                                total = recurringItems.sumOf { it.amount })
                        }
                        items(recurringItems) { item ->
                            ExpenseItemRow(
                                item,
                                templates,
                                viewModel,
                                onEditTransaction,
                                onEditTemplate,
                                counterpartyNames,
                                categoryNames
                            )
                        }
                    }
                    if (pendingItems.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            SectionHeader(
                                title = stringResource(R.string.section_pending),
                                total = pendingItems.sumOf { it.amount })
                        }
                        items(pendingItems) { item ->
                            ExpenseItemRow(
                                item,
                                templates,
                                viewModel,
                                onEditTransaction,
                                onEditTemplate,
                                counterpartyNames,
                                categoryNames
                            )
                        }
                    }
                    if (receivedItems.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            SectionHeader(
                                title = stringResource(R.string.section_received),
                                total = receivedItems.sumOf { it.amount })
                        }
                        items(receivedItems) { item ->
                            ExpenseItemRow(
                                item,
                                templates,
                                viewModel,
                                onEditTransaction,
                                onEditTemplate,
                                counterpartyNames,
                                categoryNames
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSheet) {
        FilterBottomSheet(
            filter = filter,
            categories = categories,
            accounts = accounts,
            counterparties = counterparties,
            onUpdateFilter = viewModel::updateIncomeFilter,
            onClearFilter = viewModel::clearIncomeFilter,
            onDismiss = { showSheet = false }
        )
    }
}
