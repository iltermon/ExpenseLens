package com.iltermon.expenselens.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CounterpartyDao {
    @Query("SELECT * FROM counterparties ORDER BY name ASC")
    fun getAllCounterparties(): Flow<List<Counterparty>>

    // IGNORE (not REPLACE) on the unique name: a name clash must not delete+reinsert the row,
    // which would orphan every transaction pointing at the old id. Returns -1 on conflict.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(counterparty: Counterparty): Long

    @Update
    suspend fun update(counterparty: Counterparty)

    @Delete
    suspend fun delete(counterparty: Counterparty)

    @Query("SELECT * FROM counterparties WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Counterparty?

    // Merge support: point every reference at the surviving counterparty before the stray is deleted.
    @Query("UPDATE transactions SET counterpartyId = :target WHERE counterpartyId = :source")
    suspend fun reassignTransactions(source: Int, target: Int)

    @Query("UPDATE recurring_templates SET counterpartyId = :target WHERE counterpartyId = :source")
    suspend fun reassignTemplates(source: Int, target: Int)

    // Delete support: drop dangling references so a removed counterparty leaves no orphaned ids.
    @Query("UPDATE transactions SET counterpartyId = NULL WHERE counterpartyId = :id")
    suspend fun clearTransactionRefs(id: Int)

    @Query("UPDATE recurring_templates SET counterpartyId = NULL WHERE counterpartyId = :id")
    suspend fun clearTemplateRefs(id: Int)
}
