package com.iltermon.expenselens

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iltermon.expenselens.data.ExpenseLensDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates MIGRATION_11_12, which converts categories from name-references to id-references.
 *
 * The project builds with exportSchema = false, so there is no v11 schema JSON for Room's
 * MigrationTestHelper to consume. Instead we hand-build a v11 database with the exact pre-migration
 * DDL (copied from Room's generated schema at version 11), seed it, run the migration object
 * directly, and assert both the backfilled data and the resulting table shapes.
 */
@RunWith(AndroidJUnit4::class)
class CategoryIdMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DB)
        // Open a real on-disk database at version 11 with the old, name-based schema.
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB)
            .callback(object : SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: SupportSQLiteDatabase) = createV11Schema(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        db = FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migration_backfillsCategoryIdAndDropsNameColumns() {
        // Two categories; ids autoincrement to 1 (Groceries) and 2 (Salary).
        db.execSQL("INSERT INTO categories (name, type, active) VALUES ('Groceries', 'expense', 1)")
        db.execSQL("INSERT INTO categories (name, type, active) VALUES ('Salary', 'income', 1)")

        // A transaction whose name matches a category, and one whose name matches nothing.
        db.execSQL(
            "INSERT INTO transactions (description, amount, category, date, isExpense, isPaid) " +
                "VALUES ('Aldi run', 12.0, 'Groceries', '2026-01-05', 1, 1)"
        )
        db.execSQL(
            "INSERT INTO transactions (description, amount, category, date, isExpense, isPaid) " +
                "VALUES ('Mystery', 3.0, 'Ghost Category', '2026-01-06', 1, 1)"
        )
        // A template referencing 'Salary'.
        db.execSQL(
            "INSERT INTO recurring_templates " +
                "(description, amount, category, startDate, endDate, isExpense, frequencyInterval, " +
                " frequencyUnit, autoPayment) " +
                "VALUES ('Paycheck', 2000.0, 'Salary', '2026-01-01', NULL, 0, 1, 'Month', 1)"
        )
        // A counterparty with a default category, and one with none.
        db.execSQL("INSERT INTO counterparties (name, defaultCategory) VALUES ('Aldi', 'Groceries')")
        db.execSQL("INSERT INTO counterparties (name, defaultCategory) VALUES ('Random', NULL)")

        ExpenseLensDatabase.MIGRATION_11_12.migrate(db)

        // --- Data: names resolved to ids; unmatched/NULL become NULL. ---
        assertEquals(1, longColumn("SELECT categoryId FROM transactions WHERE description = 'Aldi run'"))
        assertNull(longColumnOrNull("SELECT categoryId FROM transactions WHERE description = 'Mystery'"))
        assertEquals(2, longColumn("SELECT categoryId FROM recurring_templates WHERE description = 'Paycheck'"))
        assertEquals(1, longColumn("SELECT defaultCategoryId FROM counterparties WHERE name = 'Aldi'"))
        assertNull(longColumnOrNull("SELECT defaultCategoryId FROM counterparties WHERE name = 'Random'"))

        // Row counts preserved — nothing lost in the copy-swap.
        assertEquals(2, longColumn("SELECT COUNT(*) FROM transactions"))
        assertEquals(1, longColumn("SELECT COUNT(*) FROM recurring_templates"))
        assertEquals(2, longColumn("SELECT COUNT(*) FROM counterparties"))

        // --- Schema: new id columns present, old name columns gone. ---
        assertTrue(hasColumn("transactions", "categoryId"))
        assertFalse(hasColumn("transactions", "category"))
        assertTrue(hasColumn("recurring_templates", "categoryId"))
        assertFalse(hasColumn("recurring_templates", "category"))
        assertTrue(hasColumn("counterparties", "defaultCategoryId"))
        assertFalse(hasColumn("counterparties", "defaultCategory"))

        // --- Indexes recreated on the swapped tables. ---
        assertTrue(hasIndex("index_transactions_templateId_date"))
        assertTrue(hasIndex("index_counterparties_name"))
    }

    private fun createV11Schema(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `type` TEXT, `limitMonthly` REAL, `limitYearly` REAL, " +
                "`active` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`description` TEXT NOT NULL, `amount` REAL NOT NULL, `category` TEXT NOT NULL, " +
                "`date` TEXT NOT NULL, `isExpense` INTEGER NOT NULL, `isPaid` INTEGER NOT NULL, " +
                "`accountId` INTEGER, `templateId` INTEGER, `counterpartyId` INTEGER)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_templateId_date` " +
                "ON `transactions` (`templateId`, `date`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`description` TEXT NOT NULL, `amount` REAL NOT NULL, `category` TEXT NOT NULL, " +
                "`startDate` TEXT NOT NULL, `endDate` TEXT, `isExpense` INTEGER NOT NULL, " +
                "`frequencyInterval` INTEGER NOT NULL, `frequencyUnit` TEXT NOT NULL, " +
                "`autoPayment` INTEGER NOT NULL, `accountId` INTEGER, `counterpartyId` INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `counterparties` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `defaultCategory` TEXT, `defaultAccountId` INTEGER)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_counterparties_name` ON `counterparties` (`name`)"
        )
    }

    private fun longColumn(query: String): Long =
        longColumnOrNull(query) ?: error("expected a non-null value for: $query")

    private fun longColumnOrNull(query: String): Long? =
        db.query(query).use { c ->
            assertTrue("expected a row for: $query", c.moveToFirst())
            if (c.isNull(0)) null else c.getLong(0)
        }

    private fun hasColumn(table: String, column: String): Boolean =
        db.query("PRAGMA table_info(`$table`)").use { c ->
            val nameIdx = c.getColumnIndexOrThrow("name")
            generateSequence { if (c.moveToNext()) c.getString(nameIdx) else null }.any { it == column }
        }

    private fun hasIndex(name: String): Boolean =
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?", arrayOf(name)).use {
            it.moveToFirst()
        }

    companion object {
        private const val TEST_DB = "migration_11_12_test.db"
    }
}
