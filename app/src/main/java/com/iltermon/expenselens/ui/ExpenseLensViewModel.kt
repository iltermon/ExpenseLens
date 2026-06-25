package com.iltermon.expenselens.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.iltermon.expenselens.data.Account
import com.iltermon.expenselens.data.Category
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
    val category: String,
    val date: String,
    val isExpense: Boolean,
    val isPaid: Boolean,
    val isRecurring: Boolean,
    val transactionId: Int? = null,
    val templateId: Int? = null,          // non-null only for recurring items
    val frequencyInterval: Int? = null,   // non-null only for recurring items
    val frequencyUnit: String? = null,    // non-null only for recurring items
    val accountId: Int? = null
)
data class DateRange(val start: LocalDate, val end: LocalDate)

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
                                            category = template.category,
                                            date = dateStr,
                                            isExpense = template.isExpense,
                                            isPaid = true,
                                            accountId = template.accountId,
                                            templateId = template.id
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
    val allTemplates: StateFlow<List<RecurringTemplate>> = repository.getAllTemplates()
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
                category = t.category,
                date = t.date,
                isExpense = t.isExpense,
                isPaid = t.isPaid,
                isRecurring = false,
                transactionId = t.id,
                templateId = t.templateId,
                accountId = t.accountId
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
                        category = template.category,
                        date = date.toString(),
                        isExpense = template.isExpense,
                        isPaid = false,
                        isRecurring = true,
                        transactionId = null,
                        templateId = template.id,
                        frequencyInterval = template.frequencyInterval,
                        frequencyUnit = template.frequencyUnit,
                        accountId = template.accountId
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

    fun selectDateRange(start: LocalDate, end: LocalDate) {
        _dateRange.value = DateRange(start, end)
    }

    fun insertTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.insertTransaction(transaction) }
    }

    fun insertTemplate(template: RecurringTemplate) {
        viewModelScope.launch { repository.insertTemplate(template) }
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

    fun deleteAccount(account: Account) {
        viewModelScope.launch { repository.deleteAccount(account) }
    }

    fun insertCategory(category: Category) {
        viewModelScope.launch { repository.insertCategory(category) }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch { repository.deleteCategory(category) }
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
                        category = item.category,
                        date = item.date,
                        isExpense = item.isExpense,
                        isPaid = true,
                        templateId = item.templateId
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