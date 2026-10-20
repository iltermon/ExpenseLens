package com.iltermon.expenselens.ui

import com.iltermon.expenselens.ui.transactions.TabSections
import com.iltermon.expenselens.ui.transactions.sectionsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabSectionsTest {

    private fun item(amount: Double, isRecurring: Boolean, isPaid: Boolean, templateId: Int? = null, id: Int = 0) =
        ExpenseItem(
            id = id, description = "", amount = amount, categoryId = 1, date = "2026-10-01",
            isExpense = true, isPaid = isPaid, isRecurring = isRecurring, templateId = templateId,
        )

    private val projected = item(10.0, isRecurring = true, isPaid = false, templateId = 7, id = 1)
    private val unpaidOneTime = item(20.0, isRecurring = false, isPaid = false, id = 2)
    private val paidFromTemplate = item(40.0, isRecurring = false, isPaid = true, templateId = 7, id = 3)
    private val paidOneTime = item(80.0, isRecurring = false, isPaid = true, id = 4)
    private val items = listOf(projected, unpaidOneTime, paidFromTemplate, paidOneTime)

    @Test fun `items land in exactly one section`() {
        val s = sectionsOf(items)
        assertEquals(listOf(projected), s.recurring)
        assertEquals(listOf(unpaidOneTime), s.unpaid)
        assertEquals(listOf(paidFromTemplate, paidOneTime), s.paid)
        assertEquals(items, s.all)
    }

    @Test fun `totals match what the screens show`() {
        val s = sectionsOf(items)
        assertEquals(150.0, s.total, 1e-9)
        assertEquals(10.0, s.recurringTotal, 1e-9)
        assertEquals(20.0, s.unpaidTotal, 1e-9)
        assertEquals(120.0, s.paidTotal, 1e-9)
        assertEquals(30.0, s.outstandingTotal, 1e-9)      // still to pay: recurring + unpaid
        assertEquals(50.0, s.fromTemplatesTotal, 1e-9)    // projected 10 + recorded-from-template 40
    }

    @Test fun `order inside a section follows the input (the chosen sort)`() {
        val reversed = items.reversed()
        assertEquals(listOf(paidOneTime, paidFromTemplate), sectionsOf(reversed).paid)
    }

    @Test fun `empty tab has zero totals`() {
        val s = TabSections()
        assertTrue(s.all.isEmpty())
        assertEquals(0.0, s.total, 1e-9)
        assertEquals(0.0, s.outstandingTotal, 1e-9)
    }
}
