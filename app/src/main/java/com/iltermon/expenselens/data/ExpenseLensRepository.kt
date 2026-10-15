package com.iltermon.expenselens.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

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

    /**
     * Auto-pay: inserts a paid transaction for every due occurrence (start date through [today]) of
     * every auto-payment template that doesn't have one yet. Templates and existing keys are read
     * inside the same transaction as the inserts, so it can't interleave with [deleteSeries] and
     * resurrect rows of a series that was just deleted. Idempotent: matching is by (templateId, date).
     */
    suspend fun materializeAutoPayments (today: LocalDate) = db.withTransaction {
        val templates = db.recurringTemplateDao().getAutoPaymentTemplates()
        if (templates.isEmpty()) return@withTransaction
        val existingKeys = db.transactionDao().getTemplateOccurrenceKeys()
            .mapTo(HashSet()) { it.templateId to it.date }
        templates.forEach { template ->
            template.occurrencesInRange(LocalDate.parse(template.startDate), today).forEach { date ->
                val dateStr = date.toString()
                if (existingKeys.add(template.id to dateStr)) {
                    db.transactionDao().insert(
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

    suspend fun updateTransaction(transaction: Transaction) =
        db.transactionDao().update(transaction)

    // Recurring Templates
    fun getAllTemplates(): Flow<List<RecurringTransactionTemplate>> =
        db.recurringTemplateDao().getAllTemplates()

    suspend fun getTemplateById(id: Int): RecurringTransactionTemplate? =
        db.recurringTemplateDao().getById(id)

    suspend fun insertTemplate(template: RecurringTransactionTemplate) =
        db.recurringTemplateDao().insert(template)

    suspend fun deleteTemplate(template: RecurringTransactionTemplate) =
        db.recurringTemplateDao().delete(template)

    /**
     * Deletes a recurring series and every transaction it generated, in one transaction. Deleting
     * is for mistakes — to merely stop a series going forward, edit it and set an end date. The
     * template is removed first so the auto-pay collector can't re-create the rows mid-operation,
     * and [withTransaction] means Room fires a single invalidation after the final state is set.
     */
    suspend fun deleteSeries(template: RecurringTransactionTemplate) =
        db.withTransaction {
            db.recurringTemplateDao().delete(template)
            db.transactionDao().deleteByTemplate(template.id)
        }

    /**
     * Saves an edited template and carries its description/category/account/counterparty changes
     * over to the transactions it already generated, so a renamed or recategorised series stays
     * consistent. Labels the user edited on an individual payment are left alone.
     */
    suspend fun updateTemplate(template: RecurringTransactionTemplate) =
        db.withTransaction {
            val old = db.recurringTemplateDao().getById(template.id)
            db.recurringTemplateDao().update(template)
            if (old != null) {
                db.transactionDao().updateLabelsForTemplate(
                    templateId = template.id,
                    oldDescription = old.description,
                    description = template.description,
                    oldCategoryId = old.categoryId,
                    categoryId = template.categoryId,
                    oldAccountId = old.accountId,
                    accountId = template.accountId,
                    oldCounterpartyId = old.counterpartyId,
                    counterpartyId = template.counterpartyId
                )
            }
        }

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