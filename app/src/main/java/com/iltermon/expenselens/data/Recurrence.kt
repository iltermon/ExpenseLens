package com.iltermon.expenselens.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Expands a [RecurringTemplate] into the concrete dates on which it falls due
 * within [rangeStart]..[rangeEnd] (both inclusive).
 *
 * Occurrences are anchored at the template's [RecurringTemplate.startDate] and
 * step by [RecurringTemplate.frequencyInterval] × [RecurringTemplate.frequencyUnit].
 * The template's own [RecurringTemplate.endDate] (if set) caps the series.
 */
fun RecurringTemplate.occurrencesInRange(
    rangeStart: LocalDate,
    rangeEnd: LocalDate
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
    return result
}

private fun RecurringTemplate.occurrenceAt(anchor: LocalDate, steps: Long): LocalDate =
    when (frequencyUnit) {
        "Day" -> anchor.plusDays(steps)
        "Week" -> anchor.plusWeeks(steps)
        "Month" -> anchor.plusMonths(steps)
        "Year" -> anchor.plusYears(steps)
        else -> anchor.plusMonths(steps)
    }
