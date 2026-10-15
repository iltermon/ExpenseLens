package com.iltermon.expenselens.ui

import com.iltermon.expenselens.data.Account
import com.iltermon.expenselens.data.Category
import com.iltermon.expenselens.data.Counterparty
import com.iltermon.expenselens.ui.analytics.buildBreakdown
import com.iltermon.expenselens.ui.analytics.summarize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsCalculationsTest {

    private val salary = Category(id = 1, name = "Income A", type = "income")
    private val food = Category(id = 2, name = "Spend A", type = "expense", limitMonthly = 50.0, limitYearly = 600.0)
    private val misc = Category(id = 3, name = "Spend B", type = null)
    private val unused = Category(id = 4, name = "Spend C", type = "expense", limitMonthly = 10.0)
    private val bank = Account(id = 10, name = "Account A", type = "Debit")
    private val shop = Counterparty(id = 20, name = "Store A")

    private fun item(
        amount: Double,
        categoryId: Int,
        isExpense: Boolean = true,
        accountId: Int? = 10,
        counterpartyId: Int? = null,
        templateId: Int? = null,
    ) = ExpenseItem(
        id = 0, description = "", amount = amount, categoryId = categoryId, date = "2026-10-01",
        isExpense = isExpense, isPaid = true, isRecurring = false, templateId = templateId,
        accountId = accountId, counterpartyId = counterpartyId,
    )

    private val items = listOf(
        item(100.0, salary.id, isExpense = false),                // real income
        item(40.0, food.id, counterpartyId = shop.id),            // expense
        item(10.0, food.id, isExpense = false, counterpartyId = shop.id), // refund into an expense category
        item(25.0, misc.id, templateId = 7),                      // recurring expense
    )

    @Test fun `summary uses gross totals and counts the recurring part`() {
        val s = summarize(items)
        assertEquals(65.0, s.totalExpenses, 1e-9)
        assertEquals(110.0, s.totalIncome, 1e-9)
        assertEquals(25.0, s.recurringExpenses, 1e-9)
        assertEquals(0.0, s.recurringIncome, 1e-9)
        assertEquals(45.0, s.net, 1e-9)
    }

    @Test fun `category rows net refunds, ignore real income and keep limited categories`() {
        val rows = buildBreakdown(items, listOf(salary, food, misc, unused), listOf(bank), listOf(shop), isMonth = true)
            .categoryRows
        assertEquals(listOf("Spend A", "Spend B", "Spend C"), rows.map { it.name })
        assertEquals(30.0, rows[0].net, 1e-9)       // 40 spent - 10 refunded
        assertEquals(50.0, rows[0].limit!!, 1e-9)   // monthly limit
        assertEquals(0.0, rows[2].net, 1e-9)        // nothing spent, kept because it has a limit
        assertTrue(rows.none { it.name == "Income A" })
    }

    @Test fun `year mode uses yearly limits`() {
        val rows = buildBreakdown(items, listOf(food), emptyList(), emptyList(), isMonth = false).categoryRows
        assertEquals(600.0, rows.single().limit!!, 1e-9)
    }

    @Test fun `account rows include the recurring portion`() {
        val row = buildBreakdown(items, listOf(salary, food, misc), listOf(bank), emptyList(), isMonth = true)
            .accountRows.single()
        assertEquals(55.0, row.net, 1e-9)           // 40 - 10 + 25; salary ignored
        assertEquals(25.0, row.recurring!!, 1e-9)
    }

    @Test fun `counterparty rows only list positive net spend`() {
        val rows = buildBreakdown(items, listOf(salary, food, misc), emptyList(), listOf(shop), isMonth = true)
            .counterpartyRows
        assertEquals(30.0, rows.single().net, 1e-9)
    }
}
