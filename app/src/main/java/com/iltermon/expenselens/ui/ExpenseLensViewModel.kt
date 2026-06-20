package com.iltermon.expenselens.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.iltermon.expenselens.data.Account
import com.iltermon.expenselens.data.Category
import com.iltermon.expenselens.data.ExpenseLensRepository
import com.iltermon.expenselens.data.RecurringTemplate
import com.iltermon.expenselens.data.Transaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class ExpenseItem(
    val id: Int,
    val description: String,
    val amount: Double,
    val category: String,
    val date: String,
    val isExpense: Boolean,
    val isPaid: Boolean,
    val isRecurring: Boolean,
    val transactionId: Int? = null,
    val frequencyLabel: String? = null  // non-null only for recurring items
)
data class DateRange(val start: LocalDate, val end: LocalDate)

class ExpenseLensViewModel(private val repository: ExpenseLensRepository) : ViewModel() {

    init {
        val currentMonth = YearMonth.now()
        viewModelScope.launch {
            repository.getActiveTemplatesForMonth(currentMonth.toString())
                .combine(repository.getAllTransactions()) { templates, transactions ->
                    templates to transactions
                }
                .collect { (templates, transactions) ->
                    templates
                        .filter { it.autoPayment }
                        .filter { template ->
                            transactions.none { t ->
                                t.description == template.description &&
                                t.date.startsWith(currentMonth.toString())
                            }
                        }
                        .forEach { template ->
                            repository.insertTransaction(
                                Transaction(
                                    description = template.description,
                                    amount = template.amount,
                                    category = template.category,
                                    date = LocalDate.now().toString(),
                                    isExpense = template.isExpense,
                                    isPaid = true
                                )
                            )
                        }
                }
        }
    }

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    private val _dateRange = MutableStateFlow(
        DateRange(
            start = YearMonth.now().atDay(1),
            end = YearMonth.now().atEndOfMonth()
        )
    )

    val filteredTransactions: StateFlow<List<Transaction>> = combine(
        repository.getAllTransactions(),
        _dateRange
    ) { transactions, range ->
        transactions.filter { transaction ->
            val date = LocalDate.parse(transaction.date)
            date >= range.start && date <= range.end
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    val accounts: StateFlow<List<Account>> = repository.getAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    val allCategories: StateFlow<List<Category>> = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    val expenseCategories: StateFlow<List<Category>> = repository.getAllCategories()
        .map { list -> list.filter { it.type == null || it.type == "expense" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    val incomeCategories: StateFlow<List<Category>> = repository.getAllCategories()
        .map { list -> list.filter { it.type == null || it.type == "income" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeRecurring: StateFlow<List<RecurringTemplate>> = _selectedMonth
        .flatMapLatest { month ->
            repository.getActiveTemplatesForMonth(month.toString())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    val expenseItems: StateFlow<List<ExpenseItem>> = combine(
        filteredTransactions,
        activeRecurring
    ) { transactions, recurring ->
        val transactionItems = transactions.map { t ->
            ExpenseItem(
                id = t.id,
                description = t.description,
                amount = t.amount,
                category = t.category,
                date = t.date,
                isExpense = t.isExpense,
                isPaid = t.isPaid,
                isRecurring = false,
                transactionId = t.id
            )
        }

        val recurringItems = recurring
            .filter { template ->
                // don't show recurring if already recorded as a transaction this period
                transactions.none { t ->
                    t.description == template.description &&
                            t.date.startsWith(_dateRange.value.start.toString().substring(0, 7))
                }
            }
            .map { template ->
                val interval = template.frequencyInterval
                val unit = template.frequencyUnit
                ExpenseItem(
                    id = template.id,
                    description = template.description,
                    amount = template.amount,
                    category = template.category,
                    date = _dateRange.value.start.toString(),
                    isExpense = template.isExpense,
                    isPaid = false,
                    isRecurring = true,
                    transactionId = null,
                    frequencyLabel = if (interval == 1) unit else "Every $interval ${unit}s"
                )
            }

        (transactionItems + recurringItems).sortedBy { it.date }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

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

    fun selectDateRange(start: LocalDate, end: LocalDate) {
        _dateRange.value = DateRange(start, end)
    }

    fun insertTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.insertTransaction(transaction) }
    }

    fun insertTemplate(template: RecurringTemplate) {
        viewModelScope.launch { repository.insertTemplate(template) }
    }

    fun insertAccount(account: Account) {
        viewModelScope.launch { repository.insertAccount(account) }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch { repository.deleteAccount(account) }
    }

    fun insertCategory(category: Category) {
        viewModelScope.launch { repository.insertCategory(category) }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch { repository.deleteCategory(category) }
    }

    fun togglePaid(item: ExpenseItem) {
        viewModelScope.launch {
            if (item.isRecurring && !item.isPaid) {
                // convert recurring to real transaction
                repository.insertTransaction(
                    Transaction(
                        description = item.description,
                        amount = item.amount,
                        category = item.category,
                        date = LocalDate.now().toString(),
                        isExpense = item.isExpense,
                        isPaid = true
                    )
                )
            } else {
                item.transactionId?.let { id ->
                    val transaction = filteredTransactions.value.find { it.id == id }
                    transaction?.let {
                        repository.updateTransaction(it.copy(isPaid = !it.isPaid))
                    }
                }
            }
        }
    }
    companion object {
        private const val SUBSCRIBE_TIMEOUT_MS = 5000L

        fun factory(repository: ExpenseLensRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ExpenseLensViewModel(repository) as T
            }
    }
}