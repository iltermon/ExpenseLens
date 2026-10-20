package com.iltermon.expenselens.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.iltermon.expenselens.data.ExpenseLensRepository
import com.iltermon.expenselens.ui.DateRange
import com.iltermon.expenselens.ui.ExpenseItem
import com.iltermon.expenselens.ui.PeriodSelection
import com.iltermon.expenselens.ui.inRange
import com.iltermon.expenselens.ui.mergeItems
import com.iltermon.expenselens.ui.stateInWhileSubscribed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Year
import java.time.YearMonth

/** Which span the Analytics tab covers: the shared month (or custom range), or a whole year. */
enum class AnalyticsPeriod { MONTH, YEAR }

/**
 * State for the Analytics tab. In [AnalyticsPeriod.MONTH] mode it follows the shared [period]
 * (changing the month here moves the Expenses/Income tabs too); [AnalyticsPeriod.YEAR] is
 * Analytics-only. Totals and breakdowns are derived here once per data change, not per recomposition.
 */
class AnalyticsViewModel(
    repository: ExpenseLensRepository,
    private val period: PeriodSelection
) : ViewModel() {

    private val _mode = MutableStateFlow(AnalyticsPeriod.MONTH)
    val mode: StateFlow<AnalyticsPeriod> = _mode.asStateFlow()

    private val _year = MutableStateFlow(Year.now())
    val year: StateFlow<Year> = _year.asStateFlow()

    val selectedMonth: StateFlow<YearMonth> = period.selectedMonth

    private val range = combine(_mode, period.dateRange, _year) { mode, monthRange, year ->
        when (mode) {
            AnalyticsPeriod.MONTH -> monthRange
            AnalyticsPeriod.YEAR -> DateRange(year.atDay(1), year.atDay(year.length()))
        }
    }

    // Range filtering stays inside the merging combine (see ExpenseLensViewModel.allTransactions).
    val items: StateFlow<List<ExpenseItem>> = combine(
        repository.getAllTransactions(),
        repository.getAllTemplates(),
        range
    ) { transactions, templates, r ->
        mergeItems(transactions.inRange(r), templates, r)
    }.stateInWhileSubscribed(viewModelScope, emptyList())

    // One subscription per table, shared by the breakdown and the name lookups below.
    private val categories = repository.getAllCategories().stateInWhileSubscribed(viewModelScope, emptyList())
    private val accounts = repository.getAllAccounts().stateInWhileSubscribed(viewModelScope, emptyList())
    private val counterparties = repository.getAllCounterparties().stateInWhileSubscribed(viewModelScope, emptyList())

    val summary: StateFlow<AnalyticsSummary> = items.map(::summarize)
        .stateInWhileSubscribed(viewModelScope, AnalyticsSummary())

    val breakdown: StateFlow<AnalyticsBreakdown> = combine(
        items, categories, accounts, counterparties, _mode
    ) { items, categories, accounts, counterparties, mode ->
        buildBreakdown(items, categories, accounts, counterparties, isMonth = mode == AnalyticsPeriod.MONTH)
    }.stateInWhileSubscribed(viewModelScope, AnalyticsBreakdown())

    // id -> name lookups for the Sankey graph's node labels.
    val categoryNames: StateFlow<Map<Int, String>> = categories
        .map { list -> list.associate { it.id to it.name } }
        .stateInWhileSubscribed(viewModelScope, emptyMap())
    val accountNames: StateFlow<Map<Int, String>> = accounts
        .map { list -> list.associate { it.id to it.name } }
        .stateInWhileSubscribed(viewModelScope, emptyMap())
    val counterpartyNames: StateFlow<Map<Int, String>> = counterparties
        .map { list -> list.associate { it.id to it.name } }
        .stateInWhileSubscribed(viewModelScope, emptyMap())

    fun setMode(mode: AnalyticsPeriod) {
        _mode.value = mode
    }

    fun previous() {
        when (_mode.value) {
            AnalyticsPeriod.MONTH -> period.previousMonth()
            AnalyticsPeriod.YEAR -> _year.value = _year.value.minusYears(1)
        }
    }

    fun next() {
        when (_mode.value) {
            AnalyticsPeriod.MONTH -> period.nextMonth()
            AnalyticsPeriod.YEAR -> _year.value = _year.value.plusYears(1)
        }
    }

    /** Jumps the shared month directly (from the period picker). */
    fun setMonth(month: YearMonth) = period.setMonth(month)

    fun setYear(year: Year) {
        _year.value = year
    }

    companion object {
        fun factory(repository: ExpenseLensRepository, period: PeriodSelection): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AnalyticsViewModel(repository, period) as T
            }
    }
}
