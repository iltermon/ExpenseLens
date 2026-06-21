package com.iltermon.expenselens.ui.dev

import com.iltermon.expenselens.data.Account
import com.iltermon.expenselens.data.Category
import com.iltermon.expenselens.data.ExpenseLensRepository
import com.iltermon.expenselens.data.RecurringTemplate
import com.iltermon.expenselens.data.Transaction
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * One-time migration of the user's `ExpenseLens.xlsx` prototype into the Room database.
 * Temporary — removed once the user is fully migrated. Pure transform + load over the rows
 * produced by [XlsxReader]; the spreadsheet itself never enters the repo.
 */
object DataImporter {

    /** Account name → type. The spreadsheet has no type column; user-specified values. */
    private val ACCOUNT_TYPES = mapOf(
        "Cash" to "Cash",
        "N26" to "Bank",
        "easybank" to "Credit Card",
        "Paypal" to "Digital",
        "Wise" to "Bank",
        "Trade Republic" to "Brokerage",
    )
    private const val DEFAULT_ACCOUNT_TYPE = "Bank"

    /** Excel's 1900 date system counts days from this epoch (with its leap-year quirk baked in). */
    private val EXCEL_EPOCH = LocalDate.of(1899, 12, 30)

    data class ImportResult(
        val expenses: Int,
        val income: Int,
        val templates: Int,
        val accounts: Int,
        val categories: Int,
    )

    /**
     * Clears the DB and loads the three source sheets. Recurring templates keep their original
     * start/end dates so each occurrence lands in its real month — this is what makes per-month
     * totals match the spreadsheet. With auto-pay on, the ViewModel then backfills a paid
     * transaction for every past occurrence up to today.
     */
    suspend fun import(
        repo: ExpenseLensRepository,
        sheets: Map<String, List<List<String?>>>,
    ): ImportResult {
        // header row dropped; blank rows (no date / no description) filtered out
        val expenses = sheets["Expenses"].orEmpty().drop(1).filter { cell(it, 0) != null }
        val income = sheets["Income"].orEmpty().drop(1).filter { cell(it, 0) != null }
        val recurring = sheets["Recurring"].orEmpty().drop(1).filter { cell(it, 0) != null }

        repo.clearAll()

        // Accounts — union of every account name referenced across the three sheets.
        val accountNames = buildSet {
            expenses.forEach { cell(it, 5)?.let(::add) }
            income.forEach { cell(it, 6)?.let(::add) }
            recurring.forEach { cell(it, 6)?.let(::add) }
        }
        accountNames.forEach { name ->
            repo.insertAccount(Account(name = name, type = ACCOUNT_TYPES[name] ?: DEFAULT_ACCOUNT_TYPE))
        }
        val accountIdByName = repo.getAllAccounts().first().associate { it.name to it.id }

        // Categories — typed by which sheet(s) they appear in (null = both, shouldn't occur here).
        val expenseCats = (expenses.mapNotNull { cell(it, 2) } + recurring.mapNotNull { cell(it, 2) }).toSet()
        val incomeCats = income.mapNotNull { cell(it, 2) }.toSet()
        val allCats = expenseCats + incomeCats
        allCats.forEach { name ->
            val type = when {
                name in expenseCats && name in incomeCats -> null
                name in expenseCats -> "expense"
                else -> "income"
            }
            repo.insertCategory(Category(name = name, type = type))
        }

        // Expenses → paid expense transactions.
        var expenseCount = 0
        expenses.forEach { row ->
            val date = excelSerialToIso(cell(row, 0)) ?: return@forEach
            repo.insertTransaction(
                Transaction(
                    description = cell(row, 3).orEmpty(),
                    amount = cell(row, 4)?.toDoubleOrNull() ?: 0.0,
                    category = cell(row, 2).orEmpty(),
                    date = date,
                    isExpense = true,
                    isPaid = true,
                    accountId = cell(row, 5)?.let { accountIdByName[it] },
                    templateId = null,
                )
            )
            expenseCount++
        }

        // Income → paid income transactions.
        var incomeCount = 0
        income.forEach { row ->
            val date = excelSerialToIso(cell(row, 0)) ?: return@forEach
            repo.insertTransaction(
                Transaction(
                    description = cell(row, 3).orEmpty(),
                    amount = cell(row, 4)?.toDoubleOrNull() ?: 0.0,
                    category = cell(row, 2).orEmpty(),
                    date = date,
                    isExpense = false,
                    isPaid = true,
                    accountId = cell(row, 6)?.let { accountIdByName[it] },
                    templateId = null,
                )
            )
            incomeCount++
        }

        // Recurring → templates (auto-pay on; original start/end kept so occurrences land in
        // their real months and per-month totals match the spreadsheet).
        var templateCount = 0
        recurring.forEach { row ->
            val startDate = excelSerialToIso(cell(row, 3)) ?: return@forEach
            val (interval, unit) = mapFrequency(cell(row, 5))
            repo.insertTemplate(
                RecurringTemplate(
                    description = cell(row, 0).orEmpty(),
                    amount = cell(row, 1)?.toDoubleOrNull() ?: 0.0,
                    category = cell(row, 2).orEmpty(),
                    startDate = startDate,
                    endDate = cell(row, 4)?.let { excelSerialToIso(it) },
                    isExpense = true,
                    frequencyInterval = interval,
                    frequencyUnit = unit,
                    autoPayment = true,
                    accountId = cell(row, 6)?.let { accountIdByName[it] },
                )
            )
            templateCount++
        }

        return ImportResult(
            expenses = expenseCount,
            income = incomeCount,
            templates = templateCount,
            accounts = accountNames.size,
            categories = allCats.size,
        )
    }

    private fun cell(row: List<String?>, i: Int): String? =
        row.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }

    private fun excelSerialToIso(value: String?): String? {
        val serial = value?.toDoubleOrNull() ?: return null
        return EXCEL_EPOCH.plusDays(serial.toLong()).toString()
    }

    /** Frequency label → (interval, singular unit) matching data/Recurrence.kt's switch. */
    private fun mapFrequency(label: String?): Pair<Int, String> = when (label?.trim()) {
        "Quarterly" -> 3 to "Month"
        "Yearly" -> 1 to "Year"
        "6-Weekly" -> 6 to "Week"
        else -> 1 to "Month" // "Monthly" and anything unrecognized
    }
}
