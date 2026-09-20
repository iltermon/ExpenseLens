package com.iltermon.expenselens.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTemplateDao {
    @Query("SELECT * FROM recurring_templates")
    fun getAllTemplates(): Flow<List<RecurringTransactionTemplate>>

    @Query("SELECT * FROM recurring_templates WHERE id = :id")
    suspend fun getById(id: Int): RecurringTransactionTemplate?

    @Query("DELETE FROM recurring_templates")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: RecurringTransactionTemplate)

    @Delete
    suspend fun delete(template: RecurringTransactionTemplate)

    @Update
    suspend fun update(template: RecurringTransactionTemplate)
}