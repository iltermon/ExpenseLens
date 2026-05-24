package com.iltermon.expenselens.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Transaction::class, RecurringTemplate::class],
    version = 1,
    exportSchema = false
)
abstract class ExpenseLensDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun recurringTemplateDao(): RecurringTemplateDao

    companion object {
        @Volatile
        private var INSTANCE: ExpenseLensDatabase? = null

        fun getDatabase(context: Context): ExpenseLensDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExpenseLensDatabase::class.java,
                    "expenselens_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}