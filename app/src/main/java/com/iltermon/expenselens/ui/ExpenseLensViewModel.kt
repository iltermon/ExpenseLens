package com.iltermon.expenselens.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.iltermon.expenselens.data.Account
import com.iltermon.expenselens.data.Category
import com.iltermon.expenselens.data.Counterparty
import com.iltermon.expenselens.data.CounterpartyChoice
import com.iltermon.expenselens.data.ExpenseLensRepository
import com.iltermon.expenselens.data.RecurringTransactionTemplate
import com.iltermon.expenselens.data.Transaction
import com.iltermon.expenselens.ui.transactions.ListFilterState
import com.iltermon.expenselens.ui.transactions.SortKey
import com.iltermon.expenselens.ui.transactions.SortState
import com.iltermon.expenselens.ui.transactions.applyFilterAndSort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class ExpenseLensViewModel(private val repository: ExpenseLensRepository) : ViewModel() {

    /**
     * Auto-pay: records a paid transaction for every due occurrence of every auto-payment template
     * (see [ExpenseLensRepository.materializeAutoPayments]; idempotent, so extra runs are harmless).
     * Triggered explicitly rather than by observing the tables: on every app resume (MainActivity,
     * which also covers app start and a date change while backgrounded) and after any template write
     * or import, since those are the only things that create new due occurrences.
     */
    fun refreshAutoPayments() {
        viewModelScope.launch { repository.materializeAutoPayments(LocalDate.now()) }
    }

    /** Month / custom range shared with the Analytics tab (see [PeriodSelection]). */
    val period = PeriodSelection()
    val selectedMonth: StateFlow<YearMonth> = period.selectedMonth
    val dateRange: StateFlow<DateRange> = period.dateRange
    val isCustomRange: StateFlow<Boolean> = period.isCustomRange
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), false)

    // One shared collection of the transactions table. Range filtering happens inside the same
    // combine that merges in the templates, never in a separate upstream flow: otherwise a period
    // change reaches the merge before the re-filtered list does, and for a frame the new period's
    // occurrences are projected against the old period's payments ("recurring" rows flash up).
    private val allTransactions: StateFlow<List<Transaction>> = repository.getAllTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    val accounts: StateFlow<List<Account>> = repository.getAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    val allCategories: StateFlow<List<Category>> = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    // id -> name lookup so lists/analytics can render a categoryId. Categories are referenced by id
    // now, so display must resolve the name (mirrors the counterparty id->name map used in the UI).
    val categoryNamesById: StateFlow<Map<Int, String>> = repository.getAllCategories()
        .map { list -> list.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyMap())

    val expenseCategories: StateFlow<List<Category>> = repository.getAllCategories()
        .map { list -> list.filter { it.type == null || it.type == "expense" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    val incomeCategories: StateFlow<List<Category>> = repository.getAllCategories()
        .map { list -> list.filter { it.type == null || it.type == "income" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    // Active-only variants for the NEW-transaction pickers. Edit screens, Analytics and Settings keep
    // the unfiltered flows above so disabled accounts/categories stay visible on existing data.
    val activeAccounts: StateFlow<List<Account>> = repository.getAllAccounts()
        .map { list -> list.filter { it.active } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    val activeExpenseCategories: StateFlow<List<Category>> = repository.getAllCategories()
        .map { list -> list.filter { it.active && (it.type == null || it.type == "expense") } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    val activeIncomeCategories: StateFlow<List<Category>> = repository.getAllCategories()
        .map { list -> list.filter { it.active && (it.type == null || it.type == "income") } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val allTemplates: StateFlow<List<RecurringTransactionTemplate>> = repository.getAllTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    // All counterparties (store/vendor/payer), read from their own small table — cheap to query and
    // the source for the counterparty picker's suggestions and dedupe hints.
    val counterparties: StateFlow<List<Counterparty>> = repository.getAllCounterparties()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    // User-selected currency symbol (display only — transactions are single-currency). Persisted in
    // the app_settings key/value table so it survives restarts and is independent of the language.
    val currencySymbol: StateFlow<String> = repository.observeSetting(KEY_CURRENCY_SYMBOL)
        .map { it ?: DEFAULT_CURRENCY_SYMBOL }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), DEFAULT_CURRENCY_SYMBOL)

    fun setCurrencySymbol(symbol: String) {
        viewModelScope.launch { repository.putSetting(KEY_CURRENCY_SYMBOL, symbol) }
    }

    // True while a slow write (delete/clear) runs, so the UI can show a blocking overlay.
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } finally {
                _busy.value = false
            }
        }
    }

    private val expenseItems: StateFlow<List<ExpenseItem>> = combine(
        allTransactions,
        allTemplates,
        period.dateRange
    ) { transactions, templates, range ->
        mergeItems(transactions.inRange(range), templates, range)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    // --- Per-tab filter & sort (independent Expenses vs Income), layered on top of the date range.
    // Held here so state survives tab switches; the pure logic lives in TransactionItemFiltering.kt.
    // Applied AFTER mergeItems, so recurring projection/dedup is untouched. ---
    private val _expensesFilter = MutableStateFlow(ListFilterState())
    val expensesFilter: StateFlow<ListFilterState> = _expensesFilter.asStateFlow()
    private val _expensesSort = MutableStateFlow(SortState())
    val expensesSort: StateFlow<SortState> = _expensesSort.asStateFlow()
    private val _incomeFilter = MutableStateFlow(ListFilterState())
    val incomeFilter: StateFlow<ListFilterState> = _incomeFilter.asStateFlow()
    private val _incomeSort = MutableStateFlow(SortState())
    val incomeSort: StateFlow<SortState> = _incomeSort.asStateFlow()

    // Builds one tab's visible list: keeps only that tab's items (expense vs income) and runs them
    // through the shared filter + sort pipeline, re-emitting as a StateFlow that the screen collects.
    private fun buildTabItems(
        isExpense: Boolean,
        filter: StateFlow<ListFilterState>,
        sort: StateFlow<SortState>
    ): StateFlow<List<ExpenseItem>> = combine(
        expenseItems, filter, sort, counterparties, categoryNamesById
    ) { items, f, s, cps, catNames ->
        applyFilterAndSort(
            items.filter { it.isExpense == isExpense },
            f,
            s,
            cps.associate { it.id to it.name },
            catNames
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    val expensesTabItems: StateFlow<List<ExpenseItem>> = buildTabItems(true, _expensesFilter, _expensesSort)
    val incomeTabItems: StateFlow<List<ExpenseItem>> = buildTabItems(false, _incomeFilter, _incomeSort)

    fun updateExpensesFilter(transform: (ListFilterState) -> ListFilterState) { _expensesFilter.update(transform) }
    fun clearExpensesFilter() { _expensesFilter.value = ListFilterState()
    }
    fun setExpensesSort(key: SortKey) { _expensesSort.update { it.tapped(key) } }

    fun updateIncomeFilter(transform: (ListFilterState) -> ListFilterState) { _incomeFilter.update(transform) }
    fun clearIncomeFilter() { _incomeFilter.value = ListFilterState()
    }
    fun setIncomeSort(key: SortKey) { _incomeSort.update { it.tapped(key) } }

    fun goToPreviousMonth() = period.previousMonth()

    fun goToNextMonth() = period.nextMonth()

    fun selectDateRange(start: LocalDate, end: LocalDate) = period.selectRange(start, end)

    /** Drops a custom range, snapping the filter back to the full selected month. */
    fun clearDateRange() = period.clearRange()

    // --- Save paths used by the Add/Edit forms. The repository resolves the chosen counterparty and
    // writes the row in one transaction; template writes then record any auto-payments now due. ---
    fun saveTransaction(transaction: Transaction, choice: CounterpartyChoice) {
        viewModelScope.launch { repository.saveTransaction(transaction, choice) }
    }

    fun updateTransaction(transaction: Transaction, choice: CounterpartyChoice) {
        viewModelScope.launch { repository.updateTransaction(transaction, choice) }
    }

    fun saveTemplate(template: RecurringTransactionTemplate, choice: CounterpartyChoice) {
        viewModelScope.launch {
            repository.saveTemplate(template, choice)
            repository.materializeAutoPayments(LocalDate.now())
        }
    }

    fun updateTemplate(template: RecurringTransactionTemplate, choice: CounterpartyChoice) {
        viewModelScope.launch {
            repository.updateTemplate(template, choice)
            repository.materializeAutoPayments(LocalDate.now())
        }
    }

    fun insertCounterparty(counterparty: Counterparty) {
        viewModelScope.launch { repository.insertCounterparty(counterparty) }
    }

    fun updateCounterparty(counterparty: Counterparty) {
        viewModelScope.launch { repository.updateCounterparty(counterparty) }
    }

    /** Delete a counterparty, moving its references to [reassignTo] or nulling them when null. */
    fun deleteCounterparty(counterparty: Counterparty, reassignTo: Counterparty?) {
        launchBusy {
            if (reassignTo != null) repository.mergeCounterparties(counterparty, reassignTo)
            else repository.deleteCounterparty(counterparty)
        }
    }

    fun mergeCounterparties(source: Counterparty, target: Counterparty) {
        viewModelScope.launch { repository.mergeCounterparties(source, target) }
    }

    fun deleteTransactionById(id: Int) {
        launchBusy { repository.deleteTransactionById(id) }
    }

    fun deleteTemplate(template: RecurringTransactionTemplate) {
        launchBusy { repository.deleteSeries(template) }
    }

    suspend fun getTransactionById(id: Int): Transaction? = repository.getTransactionById(id)

    suspend fun getTemplateById(id: Int): RecurringTransactionTemplate? = repository.getTemplateById(id)

    fun insertAccount(account: Account) {
        viewModelScope.launch { repository.insertAccount(account) }
    }

    fun updateAccount(account: Account) {
        viewModelScope.launch { repository.updateAccount(account) }
    }

    fun toggleAccountActive(account: Account) {
        viewModelScope.launch { repository.updateAccount(account.copy(active = !account.active)) }
    }

    /** Delete an account, moving its references to [reassignTo] or nulling them when null. */
    fun deleteAccount(account: Account, reassignTo: Account?) {
        launchBusy { repository.deleteAccount(account, reassignTo) }
    }

    fun insertCategory(category: Category) {
        viewModelScope.launch { repository.insertCategory(category) }
    }

    /** Persist an edited category. Categories are referenced by id, so a rename needs no cascade. */
    fun updateCategory(category: Category) {
        viewModelScope.launch { repository.updateCategory(category) }
    }

    fun toggleCategoryActive(category: Category) {
        viewModelScope.launch { repository.updateCategory(category.copy(active = !category.active)) }
    }

    /** Delete a category, moving its references to [reassignTo] or nulling them when null. */
    fun deleteCategory(category: Category, reassignTo: Category?) {
        launchBusy { repository.deleteCategory(category, reassignTo) }
    }

    fun togglePaid(item: ExpenseItem) {
        viewModelScope.launch {
            if (item.isRecurring && !item.isPaid) {
                // convert this recurring occurrence into a real, paid transaction
                repository.insertTransaction(
                    Transaction(
                        description = item.description,
                        amount = item.amount,
                        categoryId = item.categoryId,
                        date = item.date,
                        isExpense = item.isExpense,
                        isPaid = true,
                        accountId = item.accountId,
                        templateId = item.templateId,
                        counterpartyId = item.counterpartyId
                    )
                )
            } else {
                item.transactionId?.let { id ->
                    repository.getTransactionById(id)?.let {
                        repository.updateTransaction(it.copy(isPaid = !it.isPaid))
                    }
                }
            }
        }
    }

    companion object {
        private const val SUBSCRIBE_TIMEOUT_MS = 5000L
        const val KEY_CURRENCY_SYMBOL = "currency_symbol"
        const val DEFAULT_CURRENCY_SYMBOL = "€"

        fun factory(repository: ExpenseLensRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ExpenseLensViewModel(repository) as T
            }
    }
}