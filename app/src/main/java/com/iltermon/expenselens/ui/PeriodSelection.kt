package com.iltermon.expenselens.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth

/**
 * The month (and optional custom date range) shared by the Expenses and Income tabs and by Analytics
 * in month mode, so changing the month anywhere moves all three. Owned by [ExpenseLensViewModel];
 * the same instance is handed to the analytics ViewModel. Both live in the activity's ViewModelStore,
 * so they share one lifetime.
 */
class PeriodSelection {
    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    private val _dateRange = MutableStateFlow(monthRange(YearMonth.now()))
    val dateRange: StateFlow<DateRange> = _dateRange.asStateFlow()

    /** True when the active range is not exactly the selected month, i.e. a custom filter is applied. */
    val isCustomRange: Flow<Boolean> = combine(_dateRange, _selectedMonth) { r, m -> r != monthRange(m) }

    fun previousMonth() = setMonth(_selectedMonth.value.minusMonths(1))

    fun nextMonth() = setMonth(_selectedMonth.value.plusMonths(1))

    /** Jumps to [month], resetting any custom range to that full month. */
    fun setMonth(month: YearMonth) {
        _selectedMonth.value = month
        _dateRange.value = monthRange(month)
    }

    fun selectRange(start: LocalDate, end: LocalDate) {
        _dateRange.value = DateRange(start, end)
    }

    /** Drops a custom range, snapping back to the full selected month. */
    fun clearRange() {
        _dateRange.value = monthRange(_selectedMonth.value)
    }

    private fun monthRange(month: YearMonth) = DateRange(month.atDay(1), month.atEndOfMonth())
}
