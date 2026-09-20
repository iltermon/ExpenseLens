package com.iltermon.expenselens.ui.analytics

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iltermon.expenselens.R
import com.iltermon.expenselens.ui.AnalyticsPeriod
import com.iltermon.expenselens.ui.ExpenseItem
import com.iltermon.expenselens.ui.ExpenseLensViewModel
import com.iltermon.expenselens.ui.util.LocalCurrencySymbol
import com.iltermon.expenselens.ui.components.PeriodStepperRow
import com.iltermon.expenselens.ui.util.SankeyOutputMode
import com.iltermon.expenselens.ui.components.SummaryBar
import com.iltermon.expenselens.ui.util.buildSankeyData
import com.iltermon.expenselens.ui.util.money
import com.iltermon.sankey.SankeyDefaults
import com.iltermon.sankey.SankeyDiagram
import java.time.Month
import java.time.Year
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.abs

/** Height of the compact Month/Year toggle in the app bar's action slot. */
private val SEGMENTED_HEIGHT = 32.dp

/**
 * Net spending for a set of items: expenses add; income subtracts **only** when it's a refund —
 * i.e. recorded into an expense/"both" category. Income posted to an income-only category (salary,
 * etc.) is real income, not a return, so it's ignored and never reduces spending.
 */
private fun netOf(items: List<ExpenseItem>, incomeOnlyCategoryIds: Set<Int>): Double =
    items.sumOf {
        when {
            it.isExpense -> it.amount
            it.categoryId in incomeOnlyCategoryIds -> 0.0
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
    val counterparties by viewModel.counterparties.collectAsState()
    val categoryNames by viewModel.categoryNamesById.collectAsState()

    val totalExpenses = items.filter { it.isExpense }.sumOf { it.amount }
    val totalIncome = items.filter { !it.isExpense }.sumOf { it.amount }
    val recurringExpenses = items.filter { it.isExpense && it.templateId != null }.sumOf { it.amount }
    val recurringIncome = items.filter { !it.isExpense && it.templateId != null }.sumOf { it.amount }
    // Net balance from the user's perspective: positive when income outpaces expenses.
    val net = totalIncome - totalExpenses

    val isMonth = period == AnalyticsPeriod.MONTH

    // Income-only categories are real income (salary, etc.), not returns — they must not reduce spend.
    val incomeOnlyCategoryIds = categories.filter { it.type == "income" }.map { it.id }.toSet()

    // Net spending per category for the active period; keep any category that was spent on OR has
    // a limit set. Sorted by spend descending (mirrors the spreadsheet's Monthly Summary).
    val categoryRows = categories.mapNotNull { cat ->
        val limit = if (isMonth) cat.limitMonthly else cat.limitYearly
        val net = netOf(items.filter { it.categoryId == cat.id }, incomeOnlyCategoryIds)
        if (net > 0 || limit != null) SpendRow(cat.name, net, null, limit) else null
    }.sortedByDescending { it.net }

    // Net spending per account, plus the recurring portion (mirrors the by-account table).
    val accountRows = accounts.mapNotNull { acc ->
        val limit = if (isMonth) acc.limitMonthly else acc.limitYearly
        val accItems = items.filter { it.accountId == acc.id }
        val net = netOf(accItems, incomeOnlyCategoryIds)
        val recurring = netOf(accItems.filter { it.templateId != null }, incomeOnlyCategoryIds)
        if (net != 0.0 || recurring != 0.0 || limit != null) {
            SpendRow(acc.name, net, recurring, limit)
        } else null
    }.sortedByDescending { it.net }

    // Net spending per counterparty (store/vendor). Counterparties carry no limit or recurring.
    val counterpartyRows = counterparties.mapNotNull { cp ->
        val net = netOf(items.filter { it.counterpartyId == cp.id }, incomeOnlyCategoryIds)
        if (net > 0) SpendRow(cp.name, net, null, null) else null
    }.sortedByDescending { it.net }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_analytics)) },
                actions = {
                    PeriodModeToggle(
                        isMonth = isMonth,
                        onModeChange = { viewModel.setAnalyticsPeriod(it) },
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )
        },
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
        Column(modifier = Modifier.padding(padding)) {
            // Pinned header — stays fixed while the summary/cards scroll beneath it.
            PeriodHeader(
                isMonth = isMonth,
                month = month,
                year = year,
                onPrevious = { viewModel.analyticsPrevious() },
                onNext = { viewModel.analyticsNext() },
                onPickMonth = { viewModel.setAnalyticsMonth(it) },
                onPickYear = { viewModel.setAnalyticsYear(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SummaryCards(
                    net = net,
                    totalExpenses = totalExpenses,
                    recurringExpenses = recurringExpenses,
                    totalIncome = totalIncome,
                    recurringIncome = recurringIncome
                )

                SpendingFlowCard(
                    items = items,
                    incomeOnlyCategoryIds = incomeOnlyCategoryIds,
                    categoryNames = categoryNames,
                    accountNames = accounts.associate { it.id to it.name },
                    counterpartyNames = counterparties.associate { it.id to it.name },
                    categoryRows = categoryRows,
                    accountRows = accountRows,
                    counterpartyRows = counterpartyRows,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodHeader(
    isMonth: Boolean,
    month: YearMonth,
    year: Year,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPickMonth: (YearMonth) -> Unit,
    onPickYear: (Year) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }

    // Same three-pill navigator as the transaction screen. In month mode the side pills show the
    // adjacent months (short) and the center shows the full month + year; in year mode they show the
    // adjacent years and the current year.
    val shortMonth = DateTimeFormatter.ofPattern("MMM")
    val fullMonth = DateTimeFormatter.ofPattern("MMMM yyyy")
    val (previousLabel, currentLabel, nextLabel) = if (isMonth) {
        Triple(
            month.minusMonths(1).format(shortMonth),
            month.format(fullMonth),
            month.plusMonths(1).format(shortMonth),
        )
    } else {
        Triple(
            (year.value - 1).toString(),
            year.value.toString(),
            (year.value + 1).toString(),
        )
    }

    PeriodStepperRow(
        previousLabel = previousLabel,
        currentLabel = currentLabel,
        nextLabel = nextLabel,
        onPrevious = onPrevious,
        onCurrent = { showPicker = true },
        onNext = onNext,
        modifier = modifier,
    )

    if (showPicker) {
        PeriodPickerDialog(
            isMonth = isMonth,
            month = month,
            year = year,
            onPickMonth = { onPickMonth(it); showPicker = false },
            onPickYear = { onPickYear(it); showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
}

/** Month/Year mode switch, hosted in the app bar's top-right action slot. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodModeToggle(
    isMonth: Boolean,
    onModeChange: (AnalyticsPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.height(SEGMENTED_HEIGHT)) {
        SegmentedButton(
            selected = isMonth,
            onClick = { onModeChange(AnalyticsPeriod.MONTH) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = {}
        ) { Text(stringResource(R.string.analytics_period_month), style = MaterialTheme.typography.labelMedium) }
        SegmentedButton(
            selected = !isMonth,
            onClick = { onModeChange(AnalyticsPeriod.YEAR) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = {}
        ) { Text(stringResource(R.string.analytics_period_year), style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun PeriodPickerDialog(
    isMonth: Boolean,
    month: YearMonth,
    year: Year,
    onPickMonth: (YearMonth) -> Unit,
    onPickYear: (Year) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        title = {
            Text(stringResource(if (isMonth) R.string.analytics_pick_month else R.string.analytics_pick_year))
        },
        text = {
            if (isMonth) {
                var pickerYear by rememberSaveable { mutableStateOf(month.year) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = { pickerYear-- }, shape = CircleShape, contentPadding = PaddingValues(12.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_previous), Modifier.size(16.dp))
                        }
                        Text(pickerYear.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { pickerYear++ }, shape = CircleShape, contentPadding = PaddingValues(12.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.cd_next), Modifier.size(16.dp))
                        }
                    }
                    GridOfCells(
                        cells = (1..12).toList(),
                        columns = 3,
                        isSelected = { m -> pickerYear == month.year && m == month.monthValue },
                        label = { m -> Month.of(m).getDisplayName(JavaTextStyle.SHORT, Locale.getDefault()) },
                        onClick = { m -> onPickMonth(YearMonth.of(pickerYear, m)) }
                    )
                }
            } else {
                val current = year.value
                GridOfCells(
                    cells = (current - 9..current + 2).toList(),
                    columns = 3,
                    isSelected = { y -> y == current },
                    label = { y -> y.toString() },
                    onClick = { y -> onPickYear(Year.of(y)) }
                )
            }
        }
    )
}

/** A simple wrap-free grid of selectable cells laid out as rows of [columns]. */
@Composable
private fun GridOfCells(
    cells: List<Int>,
    columns: Int,
    isSelected: (Int) -> Boolean,
    label: (Int) -> String,
    onClick: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(columns).forEach { rowCells ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowCells.forEach { cell ->
                    if (isSelected(cell)) {
                        FilledTonalButton(onClick = { onClick(cell) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                            Text(label(cell), maxLines = 1)
                        }
                    } else {
                        TextButton(onClick = { onClick(cell) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                            Text(label(cell), maxLines = 1)
                        }
                    }
                }
                // Pad a short final row so cells keep their column width.
                repeat(columns - rowCells.size) { Spacer(Modifier.weight(1f)) }
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
            AmountRow(stringResource(R.string.analytics_income), totalIncome, recurringIncome)
            AmountRow(stringResource(R.string.analytics_expenses), totalExpenses, recurringExpenses)
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.analytics_net), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                // Leading +/− so a surplus reads positive; the currency symbol stays next to the digits.
                val sign = if (net > 0) "+" else if (net < 0) "-" else ""
                Text(
                    sign + money(abs(net)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (net < 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
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

/**
 * Cash-flow Sankey: income categories → outputs grouped by the selected [SankeyOutputMode]. Mirrors
 * `netOf` so side totals agree with the spending cards; the diagram itself lives in the `:sankey`
 * module and stays app-agnostic (this card supplies all strings, colors, and the currency format).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpendingFlowCard(
    items: List<ExpenseItem>,
    incomeOnlyCategoryIds: Set<Int>,
    categoryNames: Map<Int, String>,
    accountNames: Map<Int, String>,
    counterpartyNames: Map<Int, String>,
    categoryRows: List<SpendRow>,
    accountRows: List<SpendRow>,
    counterpartyRows: List<SpendRow>,
) {
    var mode by rememberSaveable { mutableStateOf(SankeyOutputMode.CATEGORY) }
    var showGraph by rememberSaveable { mutableStateOf(true) }
    val unassignedLabel = stringResource(R.string.analytics_sankey_unassigned)
    val otherLabel = stringResource(R.string.analytics_sankey_other)

    val data = remember(items, mode, incomeOnlyCategoryIds, categoryNames, accountNames, counterpartyNames, unassignedLabel, otherLabel) {
        buildSankeyData(
            items, mode, incomeOnlyCategoryIds, categoryNames, accountNames, counterpartyNames,
            unassignedLabel, otherLabel,
        )
    }

    // money() is @Composable and can't be called inside the formatter lambda, so build it by hand.
    val symbol = LocalCurrencySymbol.current
    val formatter: (Double) -> String = remember(symbol) { { v -> symbol + "%.2f".format(v) } }

    val style = SankeyDefaults.style(isSystemInDarkTheme()).copy(
        labelTextStyle = MaterialTheme.typography.labelMedium,
        valueTextStyle = MaterialTheme.typography.labelSmall,
        labelColor = MaterialTheme.colorScheme.onSurface,
        valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
        valueFormatter = formatter,
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Selector (drives both the graph grouping and the text breakdown) + a corner toggle.
            Row(verticalAlignment = Alignment.CenterVertically) {
                val modes = listOf(
                    SankeyOutputMode.CATEGORY to R.string.analytics_sankey_mode_category,
                    SankeyOutputMode.ACCOUNT to R.string.analytics_sankey_mode_account,
                    SankeyOutputMode.COUNTERPARTY to R.string.analytics_sankey_mode_store,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                    modes.forEachIndexed { index, (m, label) ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick = { mode = m },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            icon = {}
                        ) { Text(stringResource(label)) }
                    }
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { showGraph = !showGraph }) {
                    if (showGraph) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.analytics_view_text)
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.ShowChart,
                            contentDescription = stringResource(R.string.analytics_view_graph)
                        )
                    }
                }
            }

            if (showGraph) {
                if (data.incomes.isEmpty() && data.outputs.isEmpty()) {
                    Text(
                        stringResource(R.string.analytics_sankey_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val maxSide = maxOf(data.incomes.size, data.outputs.size)
                    val chartHeight = (44.dp * maxSide).coerceIn(240.dp, 420.dp)
                    SankeyDiagram(
                        sources = data.incomes,
                        targets = data.outputs,
                        style = style,
                        remainderLabel = stringResource(R.string.analytics_sankey_unspent),
                        deficitLabel = stringResource(R.string.analytics_sankey_overspend),
                        hubLabel = stringResource(R.string.analytics_sankey_total_funds),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(chartHeight)
                    )
                }
            } else {
                val rows = when (mode) {
                    SankeyOutputMode.CATEGORY -> categoryRows
                    SankeyOutputMode.ACCOUNT -> accountRows
                    SankeyOutputMode.COUNTERPARTY -> counterpartyRows
                }
                if (rows.isEmpty()) {
                    Text(
                        stringResource(R.string.analytics_no_spending),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    rows.forEach { row -> SpendRowItem(row, showRecurring = mode == SankeyOutputMode.ACCOUNT) }
                }
            }
        }
    }
}
