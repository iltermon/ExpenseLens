package com.iltermon.expenselens.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
@Database(
    entities = [Transaction::class, RecurringTemplate::class, Account::class, Category::class],
    version = 7,
    exportSchema = false
)
abstract class ExpenseLensDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun recurringTemplateDao(): RecurringTemplateDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: ExpenseLensDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN isPaid INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS accounts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS categories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, type TEXT)"
                )
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN accountId INTEGER"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE recurring_templates ADD COLUMN frequencyInterval INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE recurring_templates ADD COLUMN frequencyUnit TEXT NOT NULL DEFAULT 'Monthly'"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE recurring_templates ADD COLUMN autoPayment INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // link generated transactions back to their template
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN templateId INTEGER"
                )
                // recurring_templates: month columns -> full ISO dates, plus accountId.
                // SQLite can't drop columns, so recreate the table.
                db.execSQL(
                    """
                    CREATE TABLE recurring_templates_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        description TEXT NOT NULL,
                        amount REAL NOT NULL,
                        category TEXT NOT NULL,
                        startDate TEXT NOT NULL,
                        endDate TEXT,
                        isExpense INTEGER NOT NULL,
                        frequencyInterval INTEGER NOT NULL DEFAULT 1,
                        frequencyUnit TEXT NOT NULL DEFAULT 'Monthly',
                        autoPayment INTEGER NOT NULL DEFAULT 1,
                        accountId INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO recurring_templates_new
                        (id, description, amount, category, startDate, endDate,
                         isExpense, frequencyInterval, frequencyUnit, autoPayment, accountId)
                    SELECT id, description, amount, category,
                           startMonth || '-01',
                           CASE WHEN endMonth IS NULL THEN NULL ELSE endMonth || '-01' END,
                           isExpense, frequencyInterval, frequencyUnit, autoPayment, NULL
                    FROM recurring_templates
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE recurring_templates")
                db.execSQL("ALTER TABLE recurring_templates_new RENAME TO recurring_templates")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Enforce auto-pay idempotency: at most one transaction per (templateId, date).
                // Name must match Room's generated index name for the Transaction entity.
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_templateId_date " +
                        "ON transactions (templateId, date)"
                )
            }
        }

        fun getDatabase(context: Context): ExpenseLensDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExpenseLensDatabase::class.java,
                    "expenselens_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}