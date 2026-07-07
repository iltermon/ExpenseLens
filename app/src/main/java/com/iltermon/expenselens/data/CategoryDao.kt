package com.iltermon.expenselens.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category)

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    // Categories are referenced by NAME (not id), so a rename or a delete-with-reassign must cascade
    // the name across every table that stores it: transactions, templates, and counterparty defaults.
    @Query("UPDATE transactions SET category = :newName WHERE category = :oldName")
    suspend fun renameInTransactions(oldName: String, newName: String)

    @Query("UPDATE recurring_templates SET category = :newName WHERE category = :oldName")
    suspend fun renameInTemplates(oldName: String, newName: String)

    @Query("UPDATE counterparties SET defaultCategory = :newName WHERE defaultCategory = :oldName")
    suspend fun renameInCounterpartyDefaults(oldName: String, newName: String)
}
