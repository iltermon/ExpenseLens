package com.iltermon.expenselens.ui

import java.time.LocalDate

/** An inclusive date span: the selected month, a custom filter range, or an analytics year. */
data class DateRange(val start: LocalDate, val end: LocalDate)
