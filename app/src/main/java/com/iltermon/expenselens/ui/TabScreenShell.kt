package com.iltermon.expenselens.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iltermon.expenselens.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabScreenShell(
    title: String,
    viewModel: ExpenseLensViewModel,
    onAdd: (() -> Unit)? = null,
    leftLabel: String,
    leftAmount: Double,
    rightLabel: String,
    rightAmount: Double,
    rightIsNegative: Boolean = false,
    leftRecurring: Double? = null,
    rightRecurring: Double? = null,
    // Search + sort wiring (all optional). When these are supplied the top bar grows a Search/Sort
    // pair and can flip into a full-width search field; screens own the state and clear it on exit.
    onSearchOpen: (() -> Unit)? = null,
    searchActive: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: ((String) -> Unit)? = null,
    onSearchClose: (() -> Unit)? = null,
    onOpenFilters: (() -> Unit)? = null,
    filterBadgeCount: Int = 0,
    sort: SortState? = null,
    onSelectSort: ((SortKey) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val dateRange by viewModel.dateRange.collectAsState()
    val isCustomRange by viewModel.isCustomRange.collectAsState()
    var showRangePicker by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    // System back exits search mode first, mirroring the back arrow.
    BackHandler(enabled = searchActive && onSearchClose != null) { onSearchClose?.invoke() }

    // Opening the filter sheet from search mode: dismiss the keyboard first, then open on the next tick
    // so the sheet measures at full height in one step instead of half-then-full.
    val openFilters = onOpenFilters?.let {
        {
            keyboard?.hide()
            scope.launch { delay(100); it() }
            Unit
        }
    }

    if (showRangePicker) {
        DateRangePickerDialog(
            onDismiss = { showRangePicker = false },
            onRangeSelected = { start, end ->
                viewModel.selectDateRange(start, end)
                showRangePicker = false
            }
        )
    }

    // Right-side actions, shared by both modes: (Search | Filter) then Sort then Add.
    val topActions: @Composable RowScope.() -> Unit = {
        if (searchActive) {
            if (openFilters != null) {
                BadgedBox(badge = { if (filterBadgeCount > 0) Badge { Text(filterBadgeCount.toString()) } }) {
                    IconButton(onClick = openFilters) {
                        Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.filter_open))
                    }
                }
            }
        } else if (onSearchOpen != null) {
            IconButton(onClick = onSearchOpen) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search_open))
            }
        }
        if (sort != null && onSelectSort != null) {
            Box {
                IconButton(onClick = { sortOpen = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort_open))
                }
                SortMenu(expanded = sortOpen, sort = sort, onSelect = onSelectSort, onDismiss = { sortOpen = false })
            }
        }
        if (onAdd != null) {
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (searchActive && onSearchClose != null) {
                        IconButton(onClick = onSearchClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_search_close))
                        }
                    }
                },
                title = {
                    if (searchActive) {
                        SearchField(value = searchQuery, onValueChange = { onSearchQueryChange?.invoke(it) })
                    } else {
                        Text(title)
                    }
                },
                actions = topActions
            )
        },
        bottomBar = {
            SummaryBar(
                leftLabel = leftLabel,
                leftAmount = leftAmount,
                rightLabel = rightLabel,
                rightAmount = rightAmount,
                rightIsNegative = rightIsNegative,
                leftRecurring = leftRecurring,
                rightRecurring = rightRecurring
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            MonthSelectorRow(
                selectedMonth = selectedMonth,
                dateRange = dateRange,
                isCustomRange = isCustomRange,
                onPrevious = { viewModel.goToPreviousMonth() },
                onNext = { viewModel.goToNextMonth() },
                onCurrentTapped = { showRangePicker = true },
                onClearRange = { viewModel.clearDateRange() }
            )
            content()
        }
    }
}

@Composable
private fun MonthSelectorRow(
    selectedMonth: YearMonth,
    dateRange: DateRange,
    isCustomRange: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrentTapped: () -> Unit,
    onClearRange: () -> Unit
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        // Match the list content padding (16.dp) so the navigator lines up with the rows below it and
        // with the analytics navigator, which uses the same inset.
        .padding(horizontal = 16.dp, vertical = 8.dp)

    // A custom range filter is not month-based, so the month steppers are dropped and the filter
    // takes the full width as its own banner.
    if (isCustomRange) {
        ActiveFilterBar(
            dateRange = dateRange,
            onTapped = onCurrentTapped,
            onClear = onClearRange,
            modifier = rowModifier
        )
    } else {
        MonthStepperRow(
            selectedMonth = selectedMonth,
            onPrevious = onPrevious,
            onNext = onNext,
            onCurrentTapped = onCurrentTapped,
            modifier = rowModifier
        )
    }
}

/** Month mode: step to the previous/next month with the current month tappable in the middle. */
@Composable
private fun MonthStepperRow(
    selectedMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrentTapped: () -> Unit,
    modifier: Modifier = Modifier
) {
    val monthFormatter = DateTimeFormatter.ofPattern("MMM")
    val titleFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

    PeriodStepperRow(
        previousLabel = selectedMonth.minusMonths(1).format(monthFormatter),
        currentLabel = selectedMonth.format(titleFormatter),
        nextLabel = selectedMonth.plusMonths(1).format(monthFormatter),
        onPrevious = onPrevious,
        onCurrent = onCurrentTapped,
        onNext = onNext,
        modifier = modifier,
    )
}

/** Filter mode: a full-width banner showing the active custom date range, tappable to re-pick. */
@Composable
private fun ActiveFilterBar(
    dateRange: DateRange,
    onTapped: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rangeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

    Surface(
        onClick = onTapped,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.DateRange, contentDescription = null)
                Text(
                    "${dateRange.start.format(rangeFormatter)} – ${dateRange.end.format(rangeFormatter)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_clear)
                )
            }
        }
    }
}

@Composable
internal fun SummaryBar(
    leftLabel: String,
    leftAmount: Double,
    rightLabel: String,
    rightAmount: Double,
    rightIsNegative: Boolean,
    leftRecurring: Double? = null,
    rightRecurring: Double? = null
) {
    Surface(tonalElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    leftLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    money(leftAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                RecurringCaption(leftRecurring)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    rightLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    money(rightAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (rightIsNegative) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
                RecurringCaption(rightRecurring)
            }
        }
    }
}

@Composable
private fun RecurringCaption(amount: Double?) {
    if (amount != null) {
        Text(
            stringResource(R.string.recurring_caption, money(amount)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onRangeSelected: (LocalDate, LocalDate) -> Unit
) {
    val state = rememberDateRangePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val start = state.selectedStartDateMillis
                val end = state.selectedEndDateMillis
                if (start != null && end != null) {
                    onRangeSelected(
                        LocalDate.ofEpochDay(start / 86400000),
                        LocalDate.ofEpochDay(end / 86400000)
                    )
                }
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    ) {
        val headlineFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
        DateRangePicker(
            state = state,
            modifier = Modifier.weight(1f),
            // Drop the default "Select dates" title entirely.
            title = null,
            // Smaller headline than the default large one; reuse the localized field labels as placeholders.
            headline = {
                val start = state.selectedStartDateMillis
                    ?.let { LocalDate.ofEpochDay(it / 86400000).format(headlineFormatter) }
                    ?: stringResource(R.string.form_start_date)
                val end = state.selectedEndDateMillis
                    ?.let { LocalDate.ofEpochDay(it / 86400000).format(headlineFormatter) }
                    ?: stringResource(R.string.form_end_date)
                Text(
                    "$start – $end",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
            }
        )
    }
}
