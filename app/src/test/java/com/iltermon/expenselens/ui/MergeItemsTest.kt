package com.iltermon.expenselens.ui

import com.iltermon.expenselens.data.RecurringTransactionTemplate
import com.iltermon.expenselens.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MergeItemsTest {

    private val october = DateRange(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))

    private val weekly = RecurringTransactionTemplate(
        id = 7, description = "Weekly", amount = 10.0, categoryId = 3, startDate = "2026-10-01",
        endDate = null, isExpense = true, frequencyUnit = "Week", accountId = 4, counterpartyId = 5,
    )

    private fun payment(date: String, templateId: Int? = 7, id: Int = 100) = Transaction(
        id = id, description = "Paid", amount = 10.0, date = date, isExpense = true, isPaid = true,
        templateId = templateId,
    )

    @Test fun `projects every occurrence in the range as an unpaid recurring item`() {
        val items = mergeItems(emptyList(), listOf(weekly), october)
        assertEquals(listOf("2026-10-01", "2026-10-08", "2026-10-15", "2026-10-22", "2026-10-29"), items.map { it.date })
        items.forEach {
            assertTrue(it.isRecurring)
            assertFalse(it.isPaid)
            assertEquals(null, it.transactionId)
            assertEquals(7, it.templateId)
            assertEquals(4, it.accountId)
            assertEquals(5, it.counterpartyId)
        }
    }

    @Test fun `a recorded occurrence hides its projection`() {
        val items = mergeItems(listOf(payment("2026-10-08")), listOf(weekly), october)
        assertEquals(5, items.size) // 4 projections + 1 payment, not 5 + 1
        val oct8 = items.filter { it.date == "2026-10-08" }
        assertEquals(1, oct8.size)
        assertEquals(100, oct8.single().transactionId)
        assertFalse(oct8.single().isRecurring)
    }

    @Test fun `a payment of another template or a one-time row does not hide a projection`() {
        val items = mergeItems(
            listOf(payment("2026-10-08", templateId = 99, id = 1), payment("2026-10-15", templateId = null, id = 2)),
            listOf(weekly),
            october,
        )
        assertEquals(7, items.size) // 5 projections + 2 unrelated rows
    }

    @Test fun `a payment moved off its occurrence date no longer hides it`() {
        // Why template-generated payments have a locked date (#33): the link is the exact date.
        val items = mergeItems(listOf(payment("2026-10-09")), listOf(weekly), october)
        assertTrue(items.any { it.isRecurring && it.date == "2026-10-08" })
    }

    @Test fun `result is sorted by date`() {
        val items = mergeItems(listOf(payment("2026-10-03", templateId = null)), listOf(weekly), october)
        assertEquals(items.map { it.date }.sorted(), items.map { it.date })
    }

    @Test fun `inRange keeps both ends inclusive`() {
        val rows = listOf("2026-09-30", "2026-10-01", "2026-10-31", "2026-11-01")
            .mapIndexed { i, d -> payment(d, templateId = null, id = i) }
        assertEquals(listOf("2026-10-01", "2026-10-31"), rows.inRange(october).map { it.date })
    }
}
