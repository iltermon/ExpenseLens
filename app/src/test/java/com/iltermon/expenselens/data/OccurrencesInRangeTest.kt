package com.iltermon.expenselens.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OccurrencesInRangeTest {

    private fun template(
        start: String,
        end: String? = null,
        interval: Int = 1,
        unit: String = "Month",
    ) = RecurringTransactionTemplate(
        description = "T", amount = 1.0, startDate = start, endDate = end, isExpense = true,
        frequencyInterval = interval, frequencyUnit = unit,
    )

    private fun d(s: String) = LocalDate.parse(s)
    private fun dates(vararg s: String) = s.map(::d)

    @Test fun `monthly from the 31st clamps to month end and recovers`() {
        val t = template("2026-01-31")
        assertEquals(
            dates("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"),
            t.occurrencesInRange(d("2026-01-01"), d("2026-04-30"))
        )
        // Leap year: February clamps to the 29th.
        assertEquals(
            dates("2028-02-29"),
            template("2028-01-31").occurrencesInRange(d("2028-02-01"), d("2028-02-29"))
        )
    }

    @Test fun `interval above one strides without drift`() {
        assertEquals(
            dates("2026-01-10", "2026-04-10", "2026-07-10", "2026-10-10"),
            template("2026-01-10", interval = 3).occurrencesInRange(d("2026-01-01"), d("2026-12-31"))
        )
        assertEquals(
            dates("2026-01-05", "2026-01-19", "2026-02-02"),
            template("2026-01-05", interval = 2, unit = "Week").occurrencesInRange(d("2026-01-01"), d("2026-02-10"))
        )
    }

    @Test fun `daily and yearly units`() {
        assertEquals(
            dates("2026-03-30", "2026-03-31", "2026-04-01"),
            template("2026-03-30", unit = "Day").occurrencesInRange(d("2026-03-01"), d("2026-04-01"))
        )
        assertEquals(
            dates("2026-06-15", "2027-06-15"),
            template("2026-06-15", unit = "Year").occurrencesInRange(d("2026-01-01"), d("2027-12-31"))
        )
    }

    @Test fun `end date caps the series and open-ended keeps going`() {
        assertEquals(
            dates("2026-01-01", "2026-02-01"),
            template("2026-01-01", end = "2026-02-15").occurrencesInRange(d("2026-01-01"), d("2026-12-31"))
        )
        assertEquals(12, template("2026-01-01").occurrencesInRange(d("2026-01-01"), d("2026-12-31")).size)
    }

    @Test fun `range edges are inclusive`() {
        val t = template("2026-01-15")
        assertEquals(dates("2026-02-15"), t.occurrencesInRange(d("2026-02-15"), d("2026-02-15")))
        assertEquals(dates("2026-02-15", "2026-03-15"), t.occurrencesInRange(d("2026-02-15"), d("2026-03-15")))
    }

    @Test fun `nothing before the start date or in a range without occurrences`() {
        val t = template("2026-05-20")
        assertTrue(t.occurrencesInRange(d("2026-01-01"), d("2026-05-19")).isEmpty())
        assertTrue(t.occurrencesInRange(d("2026-06-01"), d("2026-06-19")).isEmpty())
    }

    @Test fun `range far after the start jumps ahead correctly`() {
        // Exercises the "jump close to rangeStart" shortcut instead of stepping from the anchor.
        assertEquals(
            dates("2036-01-31", "2036-02-29"),
            template("2026-01-31").occurrencesInRange(d("2036-01-01"), d("2036-02-29"))
        )
    }

    @Test fun `legacy Monthly unit falls back to months`() {
        assertEquals(
            dates("2026-01-01", "2026-02-01"),
            template("2026-01-01", unit = "Monthly").occurrencesInRange(d("2026-01-01"), d("2026-02-01"))
        )
    }
}
