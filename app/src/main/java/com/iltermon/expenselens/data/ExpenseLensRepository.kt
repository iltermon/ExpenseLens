package com.iltermon.expenselens.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class ExpenseLensRepository(private val db: ExpenseLensDatabase) {

    // Transactions
    fun getAllTransactions(): Flow<List<Transaction>> =
        db.transactionDao().getAllTransactions()

    fun getTransactionsByMonth(month: String): Flow<List<Transaction>> =
        db.transactionDao().getTransactionsByMonth(month)

    suspend fun getTransactionById(id: Int): Transaction? =
        db.transactionDao().getById(id)

    suspend fun insertTransaction(transaction: Transaction) =
        db.transactionDao().insert(transaction)

    suspend fun deleteTransaction(transaction: Transaction) =
        db.transactionDao().delete(transaction)

    suspend fun deleteTransactionById(id: Int) =
        db.transactionDao().deleteById(id)

    suspend fun updateTransaction(transaction: Transaction) =
        db.transactionDao().update(transaction)

    // Recurring Templates
    fun getAllTemplates(): Flow<List<RecurringTemplate>> =
        db.recurringTemplateDao().getAllTemplates()

    suspend fun getTemplateById(id: Int): RecurringTemplate? =
        db.recurringTemplateDao().getById(id)

    suspend fun insertTemplate(template: RecurringTemplate) =
        db.recurringTemplateDao().insert(template)

    suspend fun deleteTemplate(template: RecurringTemplate) =
        db.recurringTemplateDao().delete(template)

    /**
     * Deletes a recurring series and every transaction it generated, in one transaction. Deleting
     * is for mistakes — to merely stop a series going forward, edit it and set an end date. The
     * template is removed first so the auto-pay collector can't re-create the rows mid-operation,
     * and [withTransaction] means Room fires a single invalidation after the final state is set.
     */
    suspend fun deleteSeries(template: RecurringTemplate) =
        db.withTransaction {
            db.recurringTemplateDao().delete(template)
            db.transactionDao().deleteByTemplate(template.id)
        }

    suspend fun updateTemplate(template: RecurringTemplate) =
        db.recurringTemplateDao().update(template)

    // Accounts
    fun getAllAccounts(): Flow<List<Account>> =
        db.accountDao().getAllAccounts()

    suspend fun insertAccount(account: Account) =
        db.accountDao().insert(account)

    suspend fun deleteAccount(account: Account) =
        db.accountDao().delete(account)

    // Categories
    fun getAllCategories(): Flow<List<Category>> =
        db.categoryDao().getAllCategories()

    suspend fun insertCategory(category: Category) =
        db.categoryDao().insert(category)

    suspend fun deleteCategory(category: Category) =
        db.categoryDao().delete(category)

    // Clears financial data only, leaving accounts, categories and settings intact. Templates are
    // dropped first (and the whole thing runs in one transaction) so the auto-pay collector never
    // sees "transactions gone, templates present" and re-generates the rows we're deleting.
    suspend fun clearTransactionsAndTemplates() = db.withTransaction {
        db.recurringTemplateDao().deleteAll()
        db.transactionDao().deleteAll()
    }

    // Dev-only: used by the one-time Excel importer for a fresh, deterministic load.
    suspend fun clearAll() = db.clearAllTables()

    // App settings (key/value) — e.g. the chosen currency symbol.
    fun observeSetting(key: String): Flow<String?> =
        db.appSettingDao().observe(key)

    suspend fun putSetting(key: String, value: String) =
        db.appSettingDao().upsert(AppSetting(key, value))
}