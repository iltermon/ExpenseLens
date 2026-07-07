package com.iltermon.expenselens.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
@Database(
    entities = [Transaction::class, RecurringTemplate::class, Account::class, Category::class, AppSetting::class, Counterparty::class],
    version = 12,
    exportSchema = false
)
abstract class ExpenseLensDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun recurringTemplateDao(): RecurringTemplateDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun counterpartyDao(): CounterpartyDao

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

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Additive: key/value table for app preferences. Cannot fail on existing data.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS app_settings " +
                        "(`key` TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(`key`))"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Additive: optional per-category and per-account net spending limits.
                db.execSQL("ALTER TABLE categories ADD COLUMN limitMonthly REAL")
                db.execSQL("ALTER TABLE categories ADD COLUMN limitYearly REAL")
                db.execSQL("ALTER TABLE accounts ADD COLUMN limitMonthly REAL")
                db.execSQL("ALTER TABLE accounts ADD COLUMN limitYearly REAL")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Additive: counterparties (store/vendor/payer) with curated defaults, plus a
                // nullable link from transactions and templates. Start empty — no backfill.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS counterparties " +
                        "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                        "defaultCategory TEXT, defaultAccountId INTEGER)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_counterparties_name ON counterparties (name)"
                )
                db.execSQL("ALTER TABLE transactions ADD COLUMN counterpartyId INTEGER")
                db.execSQL("ALTER TABLE recurring_templates ADD COLUMN counterpartyId INTEGER")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Additive: enable/disable flag for accounts and categories. Existing rows default to
                // active (1), so nothing is hidden until the user disables it.
                db.execSQL("ALTER TABLE accounts ADD COLUMN active INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE categories ADD COLUMN active INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Categories move from name-references to id-references, matching accounts/counterparties.
                // SQLite can't drop a column, so each table that stored a category name is recreated
                // (copy-swap, same as MIGRATION_5_6) with a nullable categoryId backfilled from the name.
                // An unmatched or NULL name yields NULL categoryId ("Uncategorized").

                // transactions: category (TEXT NOT NULL) -> categoryId (INTEGER, nullable)
                db.execSQL(
                    """
                    CREATE TABLE transactions_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        description TEXT NOT NULL,
                        amount REAL NOT NULL,
                        categoryId INTEGER,
                        date TEXT NOT NULL,
                        isExpense INTEGER NOT NULL,
                        isPaid INTEGER NOT NULL,
                        accountId INTEGER,
                        templateId INTEGER,
                        counterpartyId INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO transactions_new
                        (id, description, amount, categoryId, date, isExpense, isPaid,
                         accountId, templateId, counterpartyId)
                    SELECT id, description, amount,
                           (SELECT c.id FROM categories c WHERE c.name = transactions.category),
                           date, isExpense, isPaid, accountId, templateId, counterpartyId
                    FROM transactions
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE transactions")
                db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_templateId_date " +
                        "ON transactions (templateId, date)"
                )

                // recurring_templates: category (TEXT NOT NULL) -> categoryId (INTEGER, nullable)
                db.execSQL(
                    """
                    CREATE TABLE recurring_templates_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        description TEXT NOT NULL,
                        amount REAL NOT NULL,
                        categoryId INTEGER,
                        startDate TEXT NOT NULL,
                        endDate TEXT,
                        isExpense INTEGER NOT NULL,
                        frequencyInterval INTEGER NOT NULL,
                        frequencyUnit TEXT NOT NULL,
                        autoPayment INTEGER NOT NULL,
                        accountId INTEGER,
                        counterpartyId INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO recurring_templates_new
                        (id, description, amount, categoryId, startDate, endDate, isExpense,
                         frequencyInterval, frequencyUnit, autoPayment, accountId, counterpartyId)
                    SELECT id, description, amount,
                           (SELECT c.id FROM categories c WHERE c.name = recurring_templates.category),
                           startDate, endDate, isExpense, frequencyInterval, frequencyUnit,
                           autoPayment, accountId, counterpartyId
                    FROM recurring_templates
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE recurring_templates")
                db.execSQL("ALTER TABLE recurring_templates_new RENAME TO recurring_templates")

                // counterparties: defaultCategory (TEXT) -> defaultCategoryId (INTEGER)
                db.execSQL(
                    """
                    CREATE TABLE counterparties_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        defaultCategoryId INTEGER,
                        defaultAccountId INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO counterparties_new (id, name, defaultCategoryId, defaultAccountId)
                    SELECT id, name,
                           (SELECT c.id FROM categories c WHERE c.name = counterparties.defaultCategory),
                           defaultAccountId
                    FROM counterparties
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE counterparties")
                db.execSQL("ALTER TABLE counterparties_new RENAME TO counterparties")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_counterparties_name ON counterparties (name)"
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}