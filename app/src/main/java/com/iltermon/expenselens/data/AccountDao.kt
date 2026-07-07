package com.iltermon.expenselens.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY name ASC")
    fun getAllAccounts(): Flow<List<Account>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: Account)

    @Update
    suspend fun update(account: Account)

    @Delete
    suspend fun delete(account: Account)

    // Delete-with-move support: repoint every reference (transactions, templates, and the
    // counterparty defaults that store an account id) at the surviving account before deletion.
    @Query("UPDATE transactions SET accountId = :target WHERE accountId = :source")
    suspend fun reassignTransactions(source: Int, target: Int)

    @Query("UPDATE recurring_templates SET accountId = :target WHERE accountId = :source")
    suspend fun reassignTemplates(source: Int, target: Int)

    @Query("UPDATE counterparties SET defaultAccountId = :target WHERE defaultAccountId = :source")
    suspend fun reassignCounterpartyDefault(source: Int, target: Int)

    // Leave-unassigned support: null out dangling references so a removed account leaves no orphan ids.
    @Query("UPDATE transactions SET accountId = NULL WHERE accountId = :id")
    suspend fun clearTransactionRefs(id: Int)

    @Query("UPDATE recurring_templates SET accountId = NULL WHERE accountId = :id")
    suspend fun clearTemplateRefs(id: Int)

    @Query("UPDATE counterparties SET defaultAccountId = NULL WHERE defaultAccountId = :id")
    suspend fun clearCounterpartyDefault(id: Int)
}
