package com.iltermon.expenselens.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iltermon.expenselens.R
import java.time.format.DateTimeFormatter

/**
 * Net spending for a set of items: expenses add; income subtracts **only** when it's a refund —
 * i.e. recorded into an expense/"both" category. Income posted to an income-only category (salary,
 * etc.) is real income, not a return, so it's ignored and never reduces spending.
 */
private fun netOf(items: List<ExpenseItem>, incomeOnlyCategories: Set<String>): Double =
    items.sumOf {
        when {
            it.isExpense -> it.amount
            it.category in incomeOnlyCategories -> 0.0
            else -> -it.amount
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: ExpenseLensViewModel) {
    val items by viewModel.analyticsItems.collectAsState()
    val period by viewModel.analyticsPeriod.collectAsState()
    val month by viewModel.selectedMonth.collectAsState()
    val year by viewModel.analyticsYear.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    val totalExpenses = items.filter { it.isExpense }.sumOf { it.amount }
    val totalIncome = items.filter { !it.isExpense }.sumOf { it.amount }
    val recurringExpenses = items.filter { it.isExpense && it.templateId != null }.sumOf { it.amount }
    val recurringIncome = items.filter { !it.isExpense && it.templateId != null }.sumOf { it.amount }
    val net = totalExpenses - totalIncome

    val isMonth = period == AnalyticsPeriod.MONTH

    // Income-only categories are real income (salary, etc.), not returns — they must not reduce spend.
    val incomeOnlyCategories = categories.filter { it.type == "income" }.map { it.name }.toSet()

    // Net spending per category for the active period; keep any category that was spent on OR has
    // a limit set. Sorted by spend descending (mirrors the spreadsheet's Monthly Summary).
    val categoryRows = categories.mapNotNull { cat ->
        val limit = if (isMonth) cat.limitMonthly else cat.limitYearly
        val net = netOf(items.filter { it.category == cat.name }, incomeOnlyCategories)
        if (net > 0 || limit != null) SpendRow(cat.name, net, null, limit) else null
    }.sortedByDescending { it.net }

    // Net spending per account, plus the recurring portion (mirrors the by-account table).
    val accountRows = accounts.mapNotNull { acc ->
        val limit = if (isMonth) acc.limitMonthly else acc.limitYearly
        val accItems = items.filter { it.accountId == acc.id }
        val net = netOf(accItems, incomeOnlyCategories)
        val recurring = netOf(accItems.filter { it.templateId != null }, incomeOnlyCategories)
        if (net != 0.0 || recurring != 0.0 || limit != null) {
            SpendRow(acc.name, net, recurring, limit)
        } else null
    }.sortedByDescending { it.net }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_analytics)) }) },
        bottomBar = {
            SummaryBar(
                leftLabel = stringResource(R.string.analytics_total_expenses),
                leftAmount = totalExpenses,
                rightLabel = stringResource(R.string.analytics_total_income),
                rightAmount = totalIncome,
                rightIsNegative = false,
                leftRecurring = recurringExpenses,
                rightRecurring = recurringIncome
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PeriodHeader(
                isMonth = isMonth,
                label = if (isMonth) month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                else year.value.toString(),
                onModeChange = { viewModel.setAnalyticsPeriod(it) },
                onPrevious = { viewModel.analyticsPrevious() },
                onNext = { viewModel.analyticsNext() }
            )

            SummaryCards(
                net = net,
                totalExpenses = totalExpenses,
                recurringExpenses = recurringExpenses,
                totalIncome = totalIncome,
                recurringIncome = recurringIncome
            )

            SpendingCard(title = stringResource(R.string.analytics_spending_by_category), rows = categoryRows, showRecurring = false)
            SpendingCard(title = stringResource(R.string.analytics_spending_by_account), rows = accountRows, showRecurring = true)

            GraphPlaceholderCard()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodHeader(
    isMonth: Boolean,
    label: String,
    onModeChange: (AnalyticsPeriod) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = isMonth,
                onClick = { onModeChange(AnalyticsPeriod.MONTH) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text(stringResource(R.string.analytics_period_month)) }
            SegmentedButton(
                selected = !isMonth,
                onClick = { onModeChange(AnalyticsPeriod.YEAR) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text(stringResource(R.string.analytics_period_year)) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onPrevious, shape = RoundedCornerShape(50)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_previous),
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onNext, shape = RoundedCornerShape(50)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.cd_next),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SummaryCards(
    net: Double,
    totalExpenses: Double,
    recurringExpenses: Double,
    totalIncome: Double,
    recurringIncome: Double
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.analytics_net), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    money(net),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (net > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
            AmountRow(stringResource(R.string.analytics_expenses), totalExpenses, recurringExpenses)
            AmountRow(stringResource(R.string.analytics_income), totalIncome, recurringIncome)
        }
    }
}

@Composable
private fun AmountRow(label: String, amount: Double, recurring: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Column(horizontalAlignment = Alignment.End) {
            Text(money(amount), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                stringResource(R.string.recurring_caption, money(recurring)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** One row of a spending breakdown: net spend, optional recurring portion, optional budget limit. */
private data class SpendRow(
    val name: String,
    val net: Double,
    val recurring: Double?,
    val limit: Double?
)



@Composable
private fun SpendingCard(title: String, rows: List<SpendRow>, showRecurring: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            if (rows.isEmpty()) {
                Text(
                    stringResource(R.string.analytics_no_spending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            rows.forEach { row -> SpendRowItem(row, showRecurring) }
        }
    }
}

@Composable
private fun SpendRowItem(row: SpendRow, showRecurring: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(row.name, style = MaterialTheme.typography.bodyMedium)
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    money(row.net),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (showRecurring && row.recurring != null && row.recurring != 0.0) {
                    Text(
                        stringResource(R.string.analytics_recurring_short, money(row.recurring)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val limit = row.limit
        if (limit != null) {

            val over = row.net > limit

            LinearProgressIndicator(
                progress = { if (limit > 0) (row.net / limit).toFloat().coerceIn(0f, 1f) else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Text(
                    stringResource(R.string.analytics_limit, money(limit)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GraphPlaceholderCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.analytics_spending_over_time),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.analytics_graph_coming_soon),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
