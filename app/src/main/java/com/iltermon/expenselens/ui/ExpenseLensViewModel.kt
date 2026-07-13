package com.iltermon.expenselens.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.iltermon.expenselens.data.Account
import com.iltermon.expenselens.data.Category
import com.iltermon.expenselens.data.Counterparty
import com.iltermon.expenselens.data.ExpenseLensRepository
import com.iltermon.expenselens.data.RecurringTemplate
import com.iltermon.expenselens.data.Transaction
import com.iltermon.expenselens.data.occurrencesInRange
import com.iltermon.expenselens.ui.dev.DataImporter
import com.iltermon.expenselens.ui.dev.XlsxReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth

enum class AnalyticsPeriod { MONTH, YEAR }

data class ExpenseItem(
    val id: Int,
    val description: String,
    val amount: Double,
    val categoryId: Int?,
    val date: String,
    val isExpense: Boolean,
    val isPaid: Boolean,
    val isRecurring: Boolean,
    val transactionId: Int? = null,
    val templateId: Int? = null,          // non-null only for recurring items
    val frequencyInterval: Int? = null,   // non-null only for recurring items
    val frequencyUnit: String? = null,    // non-null only for recurring items
    val accountId: Int? = null,
    val counterpartyId: Int? = null
)
data class DateRange(val start: LocalDate, val end: LocalDate)

/**
 * How the transaction form resolved its counterparty field at save time: either an existing
 * counterparty (optionally re-curating its defaults) or a brand-new name to create.
 */
sealed interface CounterpartyChoice {
    data class Existing(val counterparty: Counterparty, val updateDefaults: Boolean) : CounterpartyChoice
    data class New(val name: String) : CounterpartyChoice
}

/** Outcome of an import/clear, kept structured so the UI can localize the message. */
sealed interface ImportStatus {
    data object Importing : ImportStatus
    data class Imported(
        val expenses: Int,
        val income: Int,
        val templates: Int,
        val accounts: Int,
        val categories: Int
    ) : ImportStatus
    data class ImportFailed(val message: String?) : ImportStatus
    data object Clearing : ImportStatus
    data object Cleared : ImportStatus
    data class ClearFailed(val message: String?) : ImportStatus
}

class ExpenseLensViewModel(private val repository: ExpenseLensRepository) : ViewModel() {

    init {
        // Auto-pay: for every auto-payment template, insert a paid transaction for each
        // due occurrence from its start date up to (and including) today. Matching is by
        // (templateId, date) so it's idempotent and frequency-aware.
        viewModelScope.launch {
            combine(
                repository.getAllTemplates(),
                repository.getAllTransactions()
            ) { templates, transactions -> templates to transactions }
               .collect { (templates, _) ->
                    val today = LocalDate.now()
                    val autoTemplates = templates.filter { it.autoPayment }
                    if (autoTemplates.isEmpty()) return@collect

                    // Idempotency snapshot. The `transactions` value from the combine above can
                    // lag behind rows we inserted on a previous firing — during a bulk import Room
                    // emits a backlog of stale lists, so trusting it re-inserts the same occurrence
                    // 2–3×. Read a fresh authoritative set instead: collect is sequential, so by the
                    // time this runs the prior firing's inserts have committed and show up here.
                    val existingKeys = repository.getAllTransactions().first()
                        .filter { it.templateId != null }
                        .mapTo(HashSet()) { it.templateId to it.date }

                    autoTemplates.forEach { template ->
                        template.occurrencesInRange(LocalDate.parse(template.startDate), today)
                            .forEach { date ->
                                val dateStr = date.toString()
                                val key = template.id to dateStr
                                if (existingKeys.add(key)) {
                                    repository.insertTransaction(
                                        Transaction(
                                            description = template.description,
                                            amount = template.amount,
                                            categoryId = template.categoryId,
                                            date = dateStr,
                                            isExpense = template.isExpense,
                                            isPaid = true,
                                            accountId = template.accountId,
                                            templateId = template.id,
                                            counterpartyId = template.counterpartyId
                                        )
                                    )
                                }
                            }
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
    val dateRange: StateFlow<DateRange> = _dateRange.asStateFlow()

    // True when the active range is not exactly the selected month, i.e. a custom filter is applied.
    val isCustomRange: StateFlow<Boolean> = combine(_dateRange, _selectedMonth) { r, m ->
        r.start != m.atDay(1) || r.end != m.atEndOfMonth()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), false)

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
    val allTemplates: StateFlow<List<RecurringTemplate>> = repository.getAllTemplates()
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

    // Merges concrete transactions (already filtered to [range]) with the projected occurrences of
    // every recurring template in [range], hiding occurrences that already exist as a transaction.
    private fun mergeItems(
        transactions: List<Transaction>,
        templates: List<RecurringTemplate>,
        range: DateRange
    ): List<ExpenseItem> {
        val transactionItems = transactions.map { t ->
            ExpenseItem(
                id = t.id,
                description = t.description,
                amount = t.amount,
                categoryId = t.categoryId,
                date = t.date,
                isExpense = t.isExpense,
                isPaid = t.isPaid,
                isRecurring = false,
                transactionId = t.id,
                templateId = t.templateId,
                accountId = t.accountId,
                counterpartyId = t.counterpartyId
            )
        }

        val recurringItems = templates.flatMap { template ->
            template.occurrencesInRange(range.start, range.end)
                // hide an occurrence that's already been recorded as a transaction
                .filter { date ->
                    transactions.none { t -> t.templateId == template.id && t.date == date.toString() }
                }
                .map { date ->
                    ExpenseItem(
                        id = template.id,
                        description = template.description,
                        amount = template.amount,
                        categoryId = template.categoryId,
                        date = date.toString(),
                        isExpense = template.isExpense,
                        isPaid = false,
                        isRecurring = true,
                        transactionId = null,
                        templateId = template.id,
                        frequencyInterval = template.frequencyInterval,
                        frequencyUnit = template.frequencyUnit,
                        accountId = template.accountId,
                        counterpartyId = template.counterpartyId
                    )
                }
        }

        return (transactionItems + recurringItems).sortedBy { it.date }
    }

    val expenseItems: StateFlow<List<ExpenseItem>> = combine(
        filteredTransactions,
        allTemplates,
        _dateRange
    ) { transactions, templates, range ->
        mergeItems(transactions, templates, range)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    // --- Per-tab filter & sort (independent Expenses vs Income), layered on top of the date range.
    // Held here like _dateRange so state survives tab switches; the pure logic lives in
    // ExpenseItemFiltering.kt. Applied AFTER mergeItems, so recurring projection/dedup and
    // analyticsItems are untouched. ---
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
    fun clearExpensesFilter() { _expensesFilter.value = ListFilterState() }
    fun setExpensesSort(key: SortKey) { _expensesSort.update { it.tapped(key) } }

    fun updateIncomeFilter(transform: (ListFilterState) -> ListFilterState) { _incomeFilter.update(transform) }
    fun clearIncomeFilter() { _incomeFilter.value = ListFilterState() }
    fun setIncomeSort(key: SortKey) { _incomeSort.update { it.tapped(key) } }

    // --- Analytics tab: in MONTH mode it shares the global month/range with the Expenses/Income
    // tabs (changing the month anywhere moves all three); the YEAR mode is Analytics-only. ---
    private val _analyticsPeriod = MutableStateFlow(AnalyticsPeriod.MONTH)
    val analyticsPeriod: StateFlow<AnalyticsPeriod> = _analyticsPeriod.asStateFlow()

    private val _analyticsYear = MutableStateFlow(Year.now())
    val analyticsYear: StateFlow<Year> = _analyticsYear.asStateFlow()

    private val analyticsDateRange: StateFlow<DateRange> = combine(
        _analyticsPeriod, _dateRange, _analyticsYear
    ) { mode, globalRange, year ->
        when (mode) {
            AnalyticsPeriod.MONTH -> globalRange
            AnalyticsPeriod.YEAR -> DateRange(year.atDay(1), year.atDay(year.length()))
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
        DateRange(YearMonth.now().atDay(1), YearMonth.now().atEndOfMonth())
    )

    private val analyticsFilteredTransactions: StateFlow<List<Transaction>> = combine(
        repository.getAllTransactions(),
        analyticsDateRange
    ) { transactions, range ->
        transactions.filter { transaction ->
            val date = LocalDate.parse(transaction.date)
            date >= range.start && date <= range.end
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    val analyticsItems: StateFlow<List<ExpenseItem>> = combine(
        analyticsFilteredTransactions,
        allTemplates,
        analyticsDateRange
    ) { transactions, templates, range ->
        mergeItems(transactions, templates, range)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    fun setAnalyticsPeriod(mode: AnalyticsPeriod) {
        _analyticsPeriod.value = mode
    }

    fun analyticsPrevious() {
        when (_analyticsPeriod.value) {
            AnalyticsPeriod.MONTH -> goToPreviousMonth()
            AnalyticsPeriod.YEAR -> _analyticsYear.value = _analyticsYear.value.minusYears(1)
        }
    }

    fun analyticsNext() {
        when (_analyticsPeriod.value) {
            AnalyticsPeriod.MONTH -> goToNextMonth()
            AnalyticsPeriod.YEAR -> _analyticsYear.value = _analyticsYear.value.plusYears(1)
        }
    }

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

    /** Jump the (shared) month directly, e.g. from the Analytics period picker. */
    fun setAnalyticsMonth(month: YearMonth) {
        _selectedMonth.value = month
        _dateRange.value = DateRange(month.atDay(1), month.atEndOfMonth())
    }

    /** Jump the Analytics-only year directly. */
    fun setAnalyticsYear(year: Year) {
        _analyticsYear.value = year
    }

    fun selectDateRange(start: LocalDate, end: LocalDate) {
        _dateRange.value = DateRange(start, end)
    }

    /** Drops a custom range, snapping the filter back to the full selected month. */
    fun clearDateRange() {
        val m = _selectedMonth.value
        _dateRange.value = DateRange(m.atDay(1), m.atEndOfMonth())
    }

    fun insertTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.insertTransaction(transaction) }
    }

    fun insertTemplate(template: RecurringTemplate) {
        viewModelScope.launch { repository.insertTemplate(template) }
    }

    // --- Counterparty-aware save paths used by the Add/Edit forms. Each resolves the chosen
    // counterparty (creating it or re-curating its defaults) before writing the row. ---
    fun saveTransaction(transaction: Transaction, choice: CounterpartyChoice) {
        viewModelScope.launch {
            val cpId = resolveCounterparty(choice, transaction.categoryId, transaction.accountId)
            repository.insertTransaction(transaction.copy(counterpartyId = cpId))
        }
    }

    fun updateTransaction(transaction: Transaction, choice: CounterpartyChoice) {
        viewModelScope.launch {
            val cpId = resolveCounterparty(choice, transaction.categoryId, transaction.accountId)
            repository.updateTransaction(transaction.copy(counterpartyId = cpId))
        }
    }

    fun saveTemplate(template: RecurringTemplate, choice: CounterpartyChoice) {
        viewModelScope.launch {
            val cpId = resolveCounterparty(choice, template.categoryId, template.accountId)
            repository.insertTemplate(template.copy(counterpartyId = cpId))
        }
    }

    fun updateTemplate(template: RecurringTemplate, choice: CounterpartyChoice) {
        viewModelScope.launch {
            val cpId = resolveCounterparty(choice, template.categoryId, template.accountId)
            repository.updateTemplate(template.copy(counterpartyId = cpId))
        }
    }

    private suspend fun resolveCounterparty(
        choice: CounterpartyChoice,
        categoryId: Int?,
        accountId: Int?
    ): Int = when (choice) {
        is CounterpartyChoice.New -> {
            val id = repository.insertCounterparty(
                Counterparty(name = choice.name, defaultCategoryId = categoryId, defaultAccountId = accountId)
            )
            // IGNORE-conflict returns -1 when the name already exists; fall back to the existing row.
            if (id > 0) id.toInt() else repository.getCounterpartyByName(choice.name)!!.id
        }
        is CounterpartyChoice.Existing -> {
            if (choice.updateDefaults) {
                repository.updateCounterparty(
                    choice.counterparty.copy(defaultCategoryId = categoryId, defaultAccountId = accountId)
                )
            }
            choice.counterparty.id
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

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.updateTransaction(transaction) }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.deleteTransaction(transaction) }
    }

    fun deleteTransactionById(id: Int) {
        launchBusy { repository.deleteTransactionById(id) }
    }

    fun updateTemplate(template: RecurringTemplate) {
        viewModelScope.launch { repository.updateTemplate(template) }
    }

    fun deleteTemplate(template: RecurringTemplate) {
        launchBusy { repository.deleteSeries(template) }
    }

    suspend fun getTransactionById(id: Int): Transaction? = repository.getTransactionById(id)

    suspend fun getTemplateById(id: Int): RecurringTemplate? = repository.getTemplateById(id)

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

    // Dev-only: one-time import of the user's ExpenseLens.xlsx (Settings → Developer Options).
    private val _importStatus = MutableStateFlow<ImportStatus?>(null)
    val importStatus: StateFlow<ImportStatus?> = _importStatus.asStateFlow()

    fun importFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _importStatus.value = ImportStatus.Importing
            try {
                // Run the whole pipeline off the main thread: clearAll() (RoomDatabase.clearAllTables)
                // is a blocking call and would otherwise crash with "cannot access database on the
                // main thread".
                val r = withContext(Dispatchers.IO) {
                    val sheets = XlsxReader.read(context, uri)
                    DataImporter.import(repository, sheets)
                }
                _importStatus.value = ImportStatus.Imported(
                    expenses = r.expenses,
                    income = r.income,
                    templates = r.templates,
                    accounts = r.accounts,
                    categories = r.categories
                )
            } catch (e: Exception) {
                _importStatus.value = ImportStatus.ImportFailed(e.message)
            }
        }
    }

    fun clearTransactions() {
        launchBusy {
            _importStatus.value = ImportStatus.Clearing
            try {
                repository.clearTransactionsAndTemplates()
                _importStatus.value = ImportStatus.Cleared
            } catch (e: Exception) {
                _importStatus.value = ImportStatus.ClearFailed(e.message)
            }
        }
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
                        templateId = item.templateId,
                        counterpartyId = item.counterpartyId
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