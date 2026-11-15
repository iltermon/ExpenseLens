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
            dates("2026-01-01", "2026-02-01", "2026-03-01"),
            template("2026-01-01", end = "2026-03-01").occurrencesInRange(d("2026-01-01"), d("2026-12-31"))
        )
        assertEquals(12, template("2026-01-01").occurrencesInRange(d("2026-01-01"), d("2026-12-31")).size)
    }

    @Test fun `end date before the next billing day is the final payment`() {
        // Issue #49: monthly on the 15th, ending 12 Feb, also pays on 12 Feb.
        assertEquals(
            dates("2026-10-15", "2026-11-15", "2026-12-15", "2027-01-15", "2027-02-12"),
            template("2026-10-15", end = "2027-02-12").occurrencesInRange(d("2026-10-01"), d("2027-12-31"))
        )
    }

    @Test fun `end date on a billing day is not counted twice`() {
        assertEquals(
            dates("2026-01-15", "2026-02-15"),
            template("2026-01-15", end = "2026-02-15").occurrencesInRange(d("2026-01-01"), d("2026-12-31"))
        )
    }

    @Test fun `end date payment respects the range`() {
        val t = template("2026-10-15", end = "2027-02-12")
        // Range holds only the end date, after the last regular occurrence.
        assertEquals(dates("2027-02-12"), t.occurrencesInRange(d("2027-02-01"), d("2027-02-28")))
        // Range stops before the end date.
        assertEquals(dates("2027-01-15"), t.occurrencesInRange(d("2027-01-01"), d("2027-02-11")))
        // Range starts after the end date.
        assertTrue(t.occurrencesInRange(d("2027-02-13"), d("2027-03-31")).isEmpty())
    }

    @Test fun `cadence only leaves out the end date payment`() {
        assertEquals(
            dates("2026-01-01", "2026-02-01"),
            template("2026-01-01", end = "2026-02-15")
                .occurrencesInRange(d("2026-01-01"), d("2026-12-31"), includeEndDatePayment = false)
        )
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
