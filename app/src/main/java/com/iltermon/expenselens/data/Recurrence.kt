package com.iltermon.expenselens.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Expands a [RecurringTransactionTemplate] into the concrete dates on which it falls due
 * within [rangeStart]..[rangeEnd] (both inclusive).
 *
 * Occurrences are anchored at the template's [RecurringTransactionTemplate.startDate] and
 * step by [RecurringTransactionTemplate.frequencyInterval] × [RecurringTransactionTemplate.frequencyUnit].
 * The template's own [RecurringTransactionTemplate.endDate] (if set) caps the series.
 *
 * When the end date falls between two billing days, the end date itself is the final payment
 * (#49): monthly on the 15th ending 12 Feb also pays on 12 Feb. Pass
 * [includeEndDatePayment] = false to get only the regular cadence.
 */
fun RecurringTransactionTemplate.occurrencesInRange(
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
    includeEndDatePayment: Boolean = true
): List<LocalDate> {
    val anchor = LocalDate.parse(startDate)
    val seriesEnd = endDate?.let { LocalDate.parse(it) }
    val effectiveEnd = if (seriesEnd != null && seriesEnd < rangeEnd) seriesEnd else rangeEnd
    if (effectiveEnd < anchor || effectiveEnd < rangeStart) return emptyList()

    val interval = frequencyInterval.coerceAtLeast(1).toLong()

    // Jump close to rangeStart instead of stepping from the anchor every time.
    val elapsed = when (frequencyUnit) {
        "Day" -> ChronoUnit.DAYS.between(anchor, rangeStart)
        "Week" -> ChronoUnit.WEEKS.between(anchor, rangeStart)
        "Month" -> ChronoUnit.MONTHS.between(anchor, rangeStart)
        "Year" -> ChronoUnit.YEARS.between(anchor, rangeStart)
        else -> ChronoUnit.MONTHS.between(anchor, rangeStart)
    }
    var k = ((elapsed / interval) - 1).coerceAtLeast(0)

    val result = mutableListOf<LocalDate>()
    while (true) {
        val date = occurrenceAt(anchor, k * interval)
        if (date > effectiveEnd) break
        if (date >= rangeStart && date >= anchor) result.add(date)
        k++
    }
    // The last regular occurrence <= seriesEnd is in result whenever it is in range, so a
    // differing last element means the end date is not a billing day.
    if (includeEndDatePayment && seriesEnd != null && seriesEnd <= rangeEnd &&
        seriesEnd >= rangeStart && result.lastOrNull() != seriesEnd
    ) {
        result.add(seriesEnd)
    }
    return result
}

/** Why a finite recurrence range can't be saved; see [recurrenceRangeError]. */
enum class RecurrenceRangeError { END_BEFORE_START, NEVER_REPEATS }

/**
 * Validates a finite series' date range against its frequency. The end date may not precede the
 * start date, and the range must reach the second occurrence, otherwise the "series" is a single
 * payment (e.g. yearly with a one-month range). Only the regular cadence counts here: the extra
 * end-date payment doesn't make a range repeat. Returns null when the range is fine.
 */
fun recurrenceRangeError(
    startDate: LocalDate,
    endDate: LocalDate,
    frequencyInterval: Int,
    frequencyUnit: String
): RecurrenceRangeError? {
    if (endDate < startDate) return RecurrenceRangeError.END_BEFORE_START
    val probe = RecurringTransactionTemplate(
        description = "",
        amount = 0.0,
        startDate = startDate.toString(),
        endDate = endDate.toString(),
        isExpense = true,
        frequencyInterval = frequencyInterval,
        frequencyUnit = frequencyUnit
    )
    return if (probe.occurrencesInRange(startDate, endDate, includeEndDatePayment = false).size < 2) RecurrenceRangeError.NEVER_REPEATS else null
}

private fun RecurringTransactionTemplate.occurrenceAt(anchor: LocalDate, steps: Long): LocalDate =
    when (frequencyUnit) {
        "Day" -> anchor.plusDays(steps)
        "Week" -> anchor.plusWeeks(steps)
        "Month" -> anchor.plusMonths(steps)
        "Year" -> anchor.plusYears(steps)
        else -> anchor.plusMonths(steps)
    }
