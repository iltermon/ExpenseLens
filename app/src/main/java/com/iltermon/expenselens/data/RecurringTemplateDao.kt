package com.iltermon.expenselens.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTemplateDao {
    @Query("SELECT * FROM recurring_templates")
    fun getAllTemplates(): Flow<List<RecurringTemplate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: RecurringTemplate)

    @Delete
    suspend fun delete(template: RecurringTemplate)

    @Update
    suspend fun update(template: RecurringTemplate)
}