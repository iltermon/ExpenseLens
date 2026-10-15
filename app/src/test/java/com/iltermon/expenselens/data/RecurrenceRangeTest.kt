package com.iltermon.expenselens.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RecurrenceRangeTest {

    private val start = LocalDate.of(2026, 1, 15)

    @Test fun `end before start is rejected`() {
        assertEquals(
            RecurrenceRangeError.END_BEFORE_START,
            recurrenceRangeError(start, start.minusDays(1), 1, "Month")
        )
    }

    @Test fun `end on the start date never repeats`() {
        assertEquals(RecurrenceRangeError.NEVER_REPEATS, recurrenceRangeError(start, start, 1, "Month"))
    }

    @Test fun `yearly series with a one-month range never repeats`() {
        assertEquals(
            RecurrenceRangeError.NEVER_REPEATS,
            recurrenceRangeError(start, start.plusMonths(1), 1, "Year")
        )
    }

    @Test fun `range ending exactly on the second occurrence is valid`() {
        assertNull(recurrenceRangeError(start, start.plusMonths(1), 1, "Month"))
        assertNull(recurrenceRangeError(start, start.plusWeeks(2), 2, "Week"))
    }

    @Test fun `range ending one day before the second occurrence never repeats`() {
        assertEquals(
            RecurrenceRangeError.NEVER_REPEATS,
            recurrenceRangeError(start, start.plusMonths(3).minusDays(1), 3, "Month")
        )
    }
}
