package com.iltermon.expenselens.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.iltermon.expenselens.data.ExpenseLensRepository
import com.iltermon.expenselens.data.RecurringTemplate
import com.iltermon.expenselens.data.Transaction
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class DateRange(val start: LocalDate, val end: LocalDate)

class ExpenseLensViewModel(private val repository: ExpenseLensRepository) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    private val _dateRange = MutableStateFlow(
        DateRange(
            start = YearMonth.now().atDay(1),
            end = YearMonth.now().atEndOfMonth()
        )
    )
    val dateRange: StateFlow<DateRange> = _dateRange.asStateFlow()

    val filteredTransactions: StateFlow<List<Transaction>> = combine(
        repository.getAllTransactions(),
        _dateRange
    ) { transactions, range ->
        transactions.filter { transaction ->
            val date = LocalDate.parse(transaction.date)
            date >= range.start && date <= range.end
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeRecurring: StateFlow<List<RecurringTemplate>> = _selectedMonth
        .flatMapLatest { month ->
            repository.getActiveTemplatesForMonth(month.toString())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun goToPreviousMonth() {
        val newMonth = _selectedMonth.value.minusMonths(1)
        _selectedMonth.value = newMonth
        _dateRange.value = DateRange(newMonth.atDay(1), newMonth.atEndOfMonth())
    }

    fun goToNextMonth() {
        val newMonth = _selectedMonth.value.plusMonths(1)
        _selectedMonth.value = newMonth
        _dateRange.value = DateRange(newMonth.atDay(1), newMonth.atEndOfMonth())
    }

    fun selectMonth(month: YearMonth) {
        _selectedMonth.value = month
        _dateRange.value = DateRange(month.atDay(1), month.atEndOfMonth())
    }

    fun selectDateRange(start: LocalDate, end: LocalDate) {
        _dateRange.value = DateRange(start, end)
    }

    fun insertTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.insertTransaction(transaction) }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.deleteTransaction(transaction) }
    }

    fun insertTemplate(template: RecurringTemplate) {
        viewModelScope.launch { repository.insertTemplate(template) }
    }

    fun deleteTemplate(template: RecurringTemplate) {
        viewModelScope.launch { repository.deleteTemplate(template) }
    }

    companion object {
        fun factory(repository: ExpenseLensRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ExpenseLensViewModel(repository) as T
            }
    }
}