package com.iltermon.expenselens.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE date LIKE :month || '%'")
    fun getTransactionsByMonth(month: String): Flow<List<Transaction>>

    @Query("SELECT templateId, date FROM transactions WHERE templateId IS NOT NULL")
    suspend fun getTemplateOccurrenceKeys(): List<TemplateOccurrenceKey>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Int): Transaction?

    @Query("DELETE FROM transactions WHERE templateId = :templateId")
    suspend fun deleteByTemplate(templateId: Int)

    // Carries a template's label changes onto the payments it already generated. Each field is only
    // overwritten where it still equals the template's old value, so a label the user changed on a
    // single payment survives a later series edit. `IS` (not `=`) so NULL matches NULL. Amount and
    // date are never touched: those payments happened at their recorded values.
    @Query(
        "UPDATE transactions SET " +
            "description = CASE WHEN description IS :oldDescription THEN :description ELSE description END, " +
            "categoryId = CASE WHEN categoryId IS :oldCategoryId THEN :categoryId ELSE categoryId END, " +
            "accountId = CASE WHEN accountId IS :oldAccountId THEN :accountId ELSE accountId END, " +
            "counterpartyId = CASE WHEN counterpartyId IS :oldCounterpartyId THEN :counterpartyId ELSE counterpartyId END " +
            "WHERE templateId = :templateId"
    )
    suspend fun updateLabelsForTemplate(
        templateId: Int,
        oldDescription: String,
        description: String,
        oldCategoryId: Int?,
        categoryId: Int?,
        oldAccountId: Int?,
        accountId: Int?,
        oldCounterpartyId: Int?,
        counterpartyId: Int?
    )

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Update
    suspend fun update(transaction: Transaction)
}

/** The (templateId, date) pair that identifies one materialised occurrence of a recurring template. */
data class TemplateOccurrenceKey(val templateId: Int, val date: String)
