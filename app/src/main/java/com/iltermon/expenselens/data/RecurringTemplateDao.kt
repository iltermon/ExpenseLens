package com.iltermon.expenselens.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTemplateDao {
    @Query("SELECT * FROM recurring_templates")
    fun getAllTemplates(): Flow<List<RecurringTemplate>>

    @Query("SELECT * FROM recurring_templates WHERE id = :id")
    suspend fun getById(id: Int): RecurringTemplate?

    @Query("DELETE FROM recurring_templates")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: RecurringTemplate)

    @Delete
    suspend fun delete(template: RecurringTemplate)

    @Update
    suspend fun update(template: RecurringTemplate)
}