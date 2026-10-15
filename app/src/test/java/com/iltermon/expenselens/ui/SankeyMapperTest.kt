package com.iltermon.expenselens.ui

import com.iltermon.expenselens.ui.util.SankeyOutputMode
import com.iltermon.expenselens.ui.util.buildSankeyData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SankeyMapperTest {

    private val categoryNames = mapOf(1 to "Salary", 2 to "Groceries", 3 to "Rent")
    private val accountNames = mapOf(10 to "Cash", 11 to "Bank")
    private val counterpartyNames = mapOf(20 to "Migros", 21 to "Landlord")

    private fun item(
        amount: Double,
        categoryId: Int? = null,
        isExpense: Boolean = true,
        accountId: Int? = null,
        counterpartyId: Int? = null,
    ) = ExpenseItem(
        id = 0, description = "", amount = amount, categoryId = categoryId,
        date = "2026-07-01", isExpense = isExpense, isPaid = true, isRecurring = false,
        accountId = accountId, counterpartyId = counterpartyId,
    )

    private fun build(items: List<ExpenseItem>, mode: SankeyOutputMode, maxNodes: Int = 6) =
        buildSankeyData(
            items, mode, categoryNames, accountNames, counterpartyNames,
            unassignedLabel = "Unassigned", otherLabel = "Other", maxNodesPerSide = maxNodes,
        )

    @Test fun `income items group into income nodes by category`() {
        val data = build(
            listOf(
                item(1000.0, categoryId = 1, isExpense = false),
                item(500.0, categoryId = 1, isExpense = false),
            ),
            SankeyOutputMode.CATEGORY,
        )
        assertEquals(1, data.incomes.size)
        assertEquals("Salary", data.incomes[0].label)
        assertEquals(1500.0, data.incomes[0].amount, 1e-9)
        assertTrue(data.outputs.isEmpty())
    }

    @Test fun `income in an expense category is income and expenses stay gross`() {
        val data = build(
            listOf(
                item(100.0, categoryId = 2, isExpense = true),
                item(30.0, categoryId = 2, isExpense = false), // e.g. a refund into Groceries
            ),
            SankeyOutputMode.CATEGORY,
        )
        assertEquals("Groceries", data.incomes.single().label)
        assertEquals(30.0, data.incomes.single().amount, 1e-9)
        assertEquals("Groceries", data.outputs.single().label)
        assertEquals(100.0, data.outputs.single().amount, 1e-9)
    }

    @Test fun `graph totals equal the summary card totals in every mode`() {
        // Mirrors AnalyticsScreen: income = all non-expense items, expenses = all expense items.
        val items = listOf(
            item(2000.0, categoryId = 1, isExpense = false, accountId = 11),
            item(40.0, categoryId = 2, isExpense = false, accountId = 10, counterpartyId = 20), // refund
            item(15.0, categoryId = null, isExpense = false),
            item(900.0, categoryId = 3, accountId = 11, counterpartyId = 21),
            item(120.0, categoryId = 2, accountId = 10, counterpartyId = 20),
            item(30.0, categoryId = null),
        )
        val cardIncome = items.filter { !it.isExpense }.sumOf { it.amount }
        val cardExpenses = items.filter { it.isExpense }.sumOf { it.amount }
        for (mode in SankeyOutputMode.entries) {
            val data = build(items, mode, maxNodes = 2) // force folding into "Other" too
            assertEquals(cardIncome, data.incomes.sumOf { it.amount }, 1e-9)
            assertEquals(cardExpenses, data.outputs.sumOf { it.amount }, 1e-9)
        }
    }

    @Test fun `node ids are unique across both sides`() {
        val items = listOf(
            item(30.0, categoryId = 2, isExpense = false),
            item(100.0, categoryId = 2),
            item(5.0, categoryId = null, isExpense = false),
            item(5.0, categoryId = null),
        )
        val data = build(items, SankeyOutputMode.CATEGORY)
        val ids = data.incomes.map { it.id } + data.outputs.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun `items without an account fall into the Unassigned bucket in ACCOUNT mode`() {
        val data = build(
            listOf(
                item(50.0, categoryId = 2, accountId = null),
                item(20.0, categoryId = 3, accountId = 10),
            ),
            SankeyOutputMode.ACCOUNT,
        )
        val unassigned = data.outputs.single { it.label == "Unassigned" }
        assertEquals(50.0, unassigned.amount, 1e-9)
        assertTrue(data.outputs.any { it.label == "Cash" })
    }

    @Test fun `the same items regroup by the active mode`() {
        val items = listOf(item(50.0, categoryId = 2, accountId = 10, counterpartyId = 20))
        assertEquals("Groceries", build(items, SankeyOutputMode.CATEGORY).outputs.single().label)
        assertEquals("Cash", build(items, SankeyOutputMode.ACCOUNT).outputs.single().label)
        assertEquals("Migros", build(items, SankeyOutputMode.COUNTERPARTY).outputs.single().label)
    }

    @Test fun `output beyond the cap folds into a single Other node`() {
        val names = (1..8).associateWith { "Cat$it" }
        val items = (1..8).map { item((100 - it).toDouble(), categoryId = it) }
        val data = buildSankeyData(
            items, SankeyOutputMode.CATEGORY, names, accountNames, counterpartyNames,
            unassignedLabel = "Unassigned", otherLabel = "Other", maxNodesPerSide = 3,
        )
        assertEquals(3, data.outputs.size)
        assertEquals("Other", data.outputs.last().label)
        // Other carries the summed tail (the 6 smallest of the 8 buckets).
        val expectedOther = items.map { it.amount }.sortedDescending().drop(2).sum()
        assertEquals(expectedOther, data.outputs.last().amount, 1e-9)
    }

    @Test fun `income categories below 10 percent of total fold into Other`() {
        val names = (1..5).associateWith { "Inc$it" }
        // Shares of 100: 50, 30, 8, 7, 5 — the last three are each < 10%.
        val items = listOf(
            item(50.0, categoryId = 1, isExpense = false),
            item(30.0, categoryId = 2, isExpense = false),
            item(8.0, categoryId = 3, isExpense = false),
            item(7.0, categoryId = 4, isExpense = false),
            item(5.0, categoryId = 5, isExpense = false),
        )
        val data = buildSankeyData(
            items, SankeyOutputMode.CATEGORY, names, accountNames, counterpartyNames,
            unassignedLabel = "Unassigned", otherLabel = "Other",
        )
        assertEquals(listOf("Inc1", "Inc2", "Other"), data.incomes.map { it.label })
        assertEquals(20.0, data.incomes.last().amount, 1e-9) // 8 + 7 + 5
    }

    @Test fun `Other folding preserves side totals so Unspent and Not covered stay intact`() {
        // The diagram derives "Unspent"/"Not covered" from the difference between the income and
        // output totals. Folding small categories into "Other" must never change those totals, or the
        // surplus/deficit node would be silently shrunk. Eight expenses overflow into "Other" here.
        val names = mapOf(1 to "Salary") + (2..9).associateWith { "Cat$it" }
        val expenses = (2..9).map { item(50.0, categoryId = it) } // 8 * 50 = 400
        val rawExpenseTotal = expenses.sumOf { it.amount }

        fun buildWithIncome(incomeAmount: Double) = buildSankeyData(
            expenses + item(incomeAmount, categoryId = 1, isExpense = false),
            SankeyOutputMode.CATEGORY, names, accountNames, counterpartyNames,
            unassignedLabel = "Unassigned", otherLabel = "Other", maxNodesPerSide = 3,
        )

        // Surplus (income 600 > expenses 400 -> "Unspent" 200).
        buildWithIncome(600.0).let { data ->
            assertEquals("Other", data.outputs.last().label) // folding actually happened
            assertEquals(rawExpenseTotal, data.outputs.sumOf { it.amount }, 1e-9)
            assertEquals(600.0, data.incomes.sumOf { it.amount }, 1e-9)
        }
        // Deficit (income 300 < expenses 400 -> "Not covered" 100).
        buildWithIncome(300.0).let { data ->
            assertEquals("Other", data.outputs.last().label)
            assertEquals(rawExpenseTotal, data.outputs.sumOf { it.amount }, 1e-9)
            assertEquals(300.0, data.incomes.sumOf { it.amount }, 1e-9)
        }
    }

    @Test fun `each side is sorted by amount descending`() {
        val data = build(
            listOf(
                item(10.0, categoryId = 2),
                item(90.0, categoryId = 3),
            ),
            SankeyOutputMode.CATEGORY,
        )
        assertEquals(listOf("Rent", "Groceries"), data.outputs.map { it.label })
    }
}
