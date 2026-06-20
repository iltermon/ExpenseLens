package com.iltermon.expenselens.data

import kotlinx.coroutines.flow.Flow

class ExpenseLensRepository(private val db: ExpenseLensDatabase) {

    // Transactions
    fun getAllTransactions(): Flow<List<Transaction>> =
        db.transactionDao().getAllTransactions()

    fun getTransactionsByMonth(month: String): Flow<List<Transaction>> =
        db.transactionDao().getTransactionsByMonth(month)

    suspend fun insertTransaction(transaction: Transaction) =
        db.transactionDao().insert(transaction)

    suspend fun deleteTransaction(transaction: Transaction) =
        db.transactionDao().delete(transaction)

    suspend fun updateTransaction(transaction: Transaction) =
        db.transactionDao().update(transaction)

    // Recurring Templates
    fun getAllTemplates(): Flow<List<RecurringTemplate>> =
        db.recurringTemplateDao().getAllTemplates()

    fun getActiveTemplatesForMonth(month: String): Flow<List<RecurringTemplate>> =
        db.recurringTemplateDao().getActiveTemplatesForMonth(month)

    suspend fun insertTemplate(template: RecurringTemplate) =
        db.recurringTemplateDao().insert(template)

    suspend fun deleteTemplate(template: RecurringTemplate) =
        db.recurringTemplateDao().delete(template)

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
}