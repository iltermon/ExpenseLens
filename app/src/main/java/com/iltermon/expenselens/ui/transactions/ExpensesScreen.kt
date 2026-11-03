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
import com.iltermon.expenselens.ui.components.ExpenseItemRow
import com.iltermon.expenselens.ui.components.FilterBottomSheet
import com.iltermon.expenselens.ui.components.NoFilterResults
import com.iltermon.expenselens.ui.components.TabScreenShell
import com.iltermon.expenselens.ui.components.SectionHeader

@Composable
fun ExpensesScreen(
    viewModel: com.iltermon.expenselens.ui.ExpenseLensViewModel,
    onAddTransaction: () -> Unit,
    onEditTransaction: (Int) -> Unit,
    onEditTemplate: (Int) -> Unit
) {
    val sections by viewModel.expensesSections.collectAsState()
    val items = sections.all
    val filter by viewModel.expensesFilter.collectAsState()
    val sort by viewModel.expensesSort.collectAsState()
    val templates by viewModel.allTemplates.collectAsState()
    val counterparties by viewModel.counterparties.collectAsState()
    val categories by viewModel.expenseCategories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val counterpartyNames = remember(counterparties) { counterparties.associate { it.id to it.name } }
    val categoryNames by viewModel.categoryNamesById.collectAsState()
    var searchActive by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }

    val recurringItems = sections.recurring
    val unpaidItems = sections.unpaid
    val paidItems = sections.paid

    TabScreenShell(
        title = stringResource(R.string.nav_expenses),
        viewModel = viewModel,
        onAdd = onAddTransaction,
        leftLabel = stringResource(R.string.expenses_this_month),
        leftAmount = sections.total,
        rightLabel = stringResource(R.string.expenses_remaining_payment),
        rightAmount = sections.outstandingTotal,
        rightIsNegative = true,
        leftRecurring = sections.fromTemplatesTotal,
        rightRecurring = sections.recurringTotal,
        onSearchOpen = { viewModel.clearExpensesFilter(); searchActive = true },
        searchActive = searchActive,
        searchQuery = filter.searchQuery,
        onSearchQueryChange = { q -> viewModel.updateExpensesFilter { it.copy(searchQuery = q) } },
        onSearchClose = { viewModel.clearExpensesFilter(); searchActive = false },
        onOpenFilters = { showSheet = true },
        filterBadgeCount = filter.advancedCount,
        sort = sort,
        onSelectSort = viewModel::setExpensesSort
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
                        NoFilterResults(
                            onClearFilters = viewModel::clearExpensesFilter
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.no_expenses_for_period))
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
                                total = sections.recurringTotal)
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
                    if (unpaidItems.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            SectionHeader(
                                title = stringResource(R.string.section_to_be_paid),
                                total = sections.unpaidTotal)
                        }
                        items(unpaidItems) { item ->
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
                    if (paidItems.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            SectionHeader(
                                title = stringResource(R.string.section_paid),
                                total = sections.paidTotal)
                        }
                        items(paidItems) { item ->
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
            onUpdateFilter = viewModel::updateExpensesFilter,
            onClearFilter = viewModel::clearExpensesFilter,
            onDismiss = { showSheet = false }
        )
    }
}
