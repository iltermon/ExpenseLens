package com.iltermon.expenselens.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iltermon.expenselens.R
import com.iltermon.expenselens.ui.components.ExpenseItemRow
import com.iltermon.expenselens.ui.components.FilterBottomSheet
import com.iltermon.expenselens.ui.components.NoFilterResults
import com.iltermon.expenselens.ui.components.TabScreenShell
import com.iltermon.expenselens.ui.util.frequencyLabel
import com.iltermon.expenselens.ui.util.money
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ExpensesScreen(
    viewModel: com.iltermon.expenselens.ui.ExpenseLensViewModel,
    onAddTransaction: () -> Unit,
    onEditTransaction: (Int) -> Unit,
    onEditTemplate: (Int) -> Unit
) {
    val items by viewModel.expensesTabItems.collectAsState()
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

    val recurringItems = items.filter { it.isRecurring && !it.isPaid }
    val unpaidItems = items.filter { !it.isRecurring && !it.isPaid }
    val paidItems = items.filter { it.isPaid }

    TabScreenShell(
        title = stringResource(R.string.nav_expenses),
        viewModel = viewModel,
        onAdd = onAddTransaction,
        leftLabel = stringResource(R.string.expenses_this_month),
        leftAmount = items.sumOf { it.amount },
        rightLabel = stringResource(R.string.expenses_remaining_payment),
        rightAmount = (recurringItems + unpaidItems).sumOf { it.amount },
        rightIsNegative = true,
        leftRecurring = items.filter { it.templateId != null }.sumOf { it.amount },
        rightRecurring = recurringItems.sumOf { it.amount },
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
                    if (unpaidItems.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            SectionHeader(
                                title = stringResource(R.string.section_to_be_paid),
                                total = unpaidItems.sumOf { it.amount })
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
                                total = paidItems.sumOf { it.amount })
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

@Composable
fun SectionHeader(title: String, total: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            money(total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ExpenseItemCard(
    item: com.iltermon.expenselens.ui.ExpenseItem,
    onTogglePaid: (com.iltermon.expenselens.ui.ExpenseItem) -> Unit,
    onClick: () -> Unit = {},
    counterpartyName: String? = null,
    categoryName: String? = null,
    remainingOccurrences: Int? = null
) {
    val categoryLabel = categoryName ?: stringResource(R.string.uncategorized)
    val dateFormatter = DateTimeFormatter.ofPattern("d MMM")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isPaid)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledIconButton(
                onClick = { onTogglePaid(item) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (item.isPaid)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.tertiary
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Text(if (item.isPaid) "✓" else "−")
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (!counterpartyName.isNullOrBlank()) "$categoryLabel · $counterpartyName" else categoryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.frequencyUnit != null) {
                    val frequency = stringResource(
                        R.string.recurring_frequency_prefix,
                        frequencyLabel(item.frequencyInterval ?: 1, item.frequencyUnit)
                    )
                    Text(
                        if (remainingOccurrences != null)
                            "$frequency · ${stringResource(R.string.recurring_remaining, remainingOccurrences)}"
                        else frequency,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    money(item.amount),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.isExpense) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
                Text(
                    LocalDate.parse(item.date).format(dateFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
