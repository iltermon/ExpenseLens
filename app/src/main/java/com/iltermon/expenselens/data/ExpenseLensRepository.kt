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

    // Rename / type / limits / active — id is stable, so no reference cascade is needed.
    suspend fun updateAccount(account: Account) =
        db.accountDao().update(account)

    /**
     * Removes an account after moving its transactions/templates/counterparty-defaults to
     * [reassignTo], or nulling those references when [reassignTo] is null ("leave unassigned").
     */
    suspend fun deleteAccount(account: Account, reassignTo: Account?) = db.withTransaction {
        if (reassignTo != null) {
            db.accountDao().reassignTransactions(account.id, reassignTo.id)
            db.accountDao().reassignTemplates(account.id, reassignTo.id)
            db.accountDao().reassignCounterpartyDefault(account.id, reassignTo.id)
        } else {
            db.accountDao().clearTransactionRefs(account.id)
            db.accountDao().clearTemplateRefs(account.id)
            db.accountDao().clearCounterpartyDefault(account.id)
        }
        db.accountDao().delete(account)
    }

    // Categories
    fun getAllCategories(): Flow<List<Category>> =
        db.categoryDao().getAllCategories()

    suspend fun insertCategory(category: Category) =
        db.categoryDao().insert(category)

    // Rename / type / limits / active — id is stable, so no reference cascade is needed.
    suspend fun updateCategory(category: Category) =
        db.categoryDao().update(category)

    /**
     * Removes a category after moving its transactions/templates/counterparty-defaults to
     * [reassignTo], or nulling those references when [reassignTo] is null ("leave unassigned").
     */
    suspend fun deleteCategory(category: Category, reassignTo: Category?) = db.withTransaction {
        if (reassignTo != null) {
            db.categoryDao().reassignTransactions(category.id, reassignTo.id)
            db.categoryDao().reassignTemplates(category.id, reassignTo.id)
            db.categoryDao().reassignCounterpartyDefault(category.id, reassignTo.id)
        } else {
            db.categoryDao().clearTransactionRefs(category.id)
            db.categoryDao().clearTemplateRefs(category.id)
            db.categoryDao().clearCounterpartyDefault(category.id)
        }
        db.categoryDao().delete(category)
    }

    // Counterparties
    fun getAllCounterparties(): Flow<List<Counterparty>> =
        db.counterpartyDao().getAllCounterparties()

    suspend fun insertCounterparty(counterparty: Counterparty): Long =
        db.counterpartyDao().insert(counterparty)

    suspend fun updateCounterparty(counterparty: Counterparty) =
        db.counterpartyDao().update(counterparty)

    suspend fun getCounterpartyByName(name: String): Counterparty? =
        db.counterpartyDao().getByName(name)

    /** Reassigns every reference from [source] to [target], then removes [source]. */
    suspend fun mergeCounterparties(source: Counterparty, target: Counterparty) = db.withTransaction {
        db.counterpartyDao().reassignTransactions(source.id, target.id)
        db.counterpartyDao().reassignTemplates(source.id, target.id)
        db.counterpartyDao().delete(source)
    }

    /** Removes a counterparty, nulling the references transactions/templates hold to it. */
    suspend fun deleteCounterparty(counterparty: Counterparty) = db.withTransaction {
        db.counterpartyDao().clearTransactionRefs(counterparty.id)
        db.counterpartyDao().clearTemplateRefs(counterparty.id)
        db.counterpartyDao().delete(counterparty)
    }

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