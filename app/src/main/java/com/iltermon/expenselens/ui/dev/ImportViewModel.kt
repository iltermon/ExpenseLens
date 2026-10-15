package com.iltermon.expenselens.ui.dev

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.iltermon.expenselens.data.ExpenseLensRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

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

/**
 * Settings → one-time import of the user's ExpenseLens.xlsx, and "clear transactions". Kept in
 * release builds on purpose (#29 B2 won't-do).
 */
class ImportViewModel(private val repository: ExpenseLensRepository) : ViewModel() {

    private val _importStatus = MutableStateFlow<ImportStatus?>(null)
    val importStatus: StateFlow<ImportStatus?> = _importStatus.asStateFlow()

    // True while clearing, so the app shows its blocking overlay (see AppNavigation).
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

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
                        .also { repository.materializeAutoPayments(LocalDate.now()) }
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
        viewModelScope.launch {
            _busy.value = true
            _importStatus.value = ImportStatus.Clearing
            try {
                repository.clearTransactionsAndTemplates()
                _importStatus.value = ImportStatus.Cleared
            } catch (e: Exception) {
                _importStatus.value = ImportStatus.ClearFailed(e.message)
            } finally {
                _busy.value = false
            }
        }
    }

    companion object {
        fun factory(repository: ExpenseLensRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ImportViewModel(repository) as T
            }
    }
}
