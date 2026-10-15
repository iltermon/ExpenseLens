package com.iltermon.expenselens.ui

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PeriodSelectionTest {

    private val march = YearMonth.of(2026, 3)

    @Test fun `setMonth selects the full month`() {
        val p = PeriodSelection()
        p.setMonth(march)
        assertEquals(march, p.selectedMonth.value)
        assertEquals(DateRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)), p.dateRange.value)
    }

    @Test fun `previous and next step one month and reset a custom range`() {
        val p = PeriodSelection()
        p.setMonth(march)
        p.selectRange(LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 9))
        p.nextMonth()
        assertEquals(YearMonth.of(2026, 4), p.selectedMonth.value)
        assertEquals(LocalDate.of(2026, 4, 30), p.dateRange.value.end)
        p.previousMonth()
        p.previousMonth()
        assertEquals(YearMonth.of(2026, 2), p.selectedMonth.value)
    }

    @Test fun `custom range is flagged until cleared`() = runBlocking {
        val p = PeriodSelection()
        p.setMonth(march)
        assertFalse(p.isCustomRange.first())
        p.selectRange(LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 9))
        assertTrue(p.isCustomRange.first())
        p.clearRange()
        assertFalse(p.isCustomRange.first())
        assertEquals(LocalDate.of(2026, 3, 1), p.dateRange.value.start)
    }
}
