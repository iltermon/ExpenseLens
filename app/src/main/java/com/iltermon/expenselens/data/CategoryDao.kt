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

    // Categories are referenced by id, so a rename needs no cascade. A delete reassigns the id to a
    // replacement category (or nulls it — "leave unassigned") across transactions, templates, and
    // counterparty defaults, mirroring the account pattern in AccountDao.
    @Query("UPDATE transactions SET categoryId = :target WHERE categoryId = :source")
    suspend fun reassignTransactions(source: Int, target: Int)

    @Query("UPDATE recurring_templates SET categoryId = :target WHERE categoryId = :source")
    suspend fun reassignTemplates(source: Int, target: Int)

    @Query("UPDATE counterparties SET defaultCategoryId = :target WHERE defaultCategoryId = :source")
    suspend fun reassignCounterpartyDefault(source: Int, target: Int)

    @Query("UPDATE transactions SET categoryId = NULL WHERE categoryId = :id")
    suspend fun clearTransactionRefs(id: Int)

    @Query("UPDATE recurring_templates SET categoryId = NULL WHERE categoryId = :id")
    suspend fun clearTemplateRefs(id: Int)

    @Query("UPDATE counterparties SET defaultCategoryId = NULL WHERE defaultCategoryId = :id")
    suspend fun clearCounterpartyDefault(id: Int)
}
