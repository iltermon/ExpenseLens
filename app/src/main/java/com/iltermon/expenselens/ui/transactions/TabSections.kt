package com.iltermon.expenselens.ui.transactions

import com.iltermon.expenselens.ui.ExpenseItem

/**
 * One Expenses/Income tab split into its three list sections, with every total the screen shows.
 * Built once per data change in the ViewModel (see [sectionsOf]) instead of on every recomposition.
 */
data class TabSections(
    /** Everything in the tab (after filter and sort), for the empty check and the flat sorted list. */
    val all: List<ExpenseItem> = emptyList(),
    /** Projected occurrences of recurring templates that aren't recorded yet. */
    val recurring: List<ExpenseItem> = emptyList(),
    /** Recorded but not paid yet ("To be paid" / "Pending"). */
    val unpaid: List<ExpenseItem> = emptyList(),
    /** Recorded and paid ("Paid" / "Received"). */
    val paid: List<ExpenseItem> = emptyList(),
) {
    val total: Double = all.sumOf { it.amount }
    val recurringTotal: Double = recurring.sumOf { it.amount }
    val unpaidTotal: Double = unpaid.sumOf { it.amount }
    val paidTotal: Double = paid.sumOf { it.amount }

    /** Still to pay / receive: projected recurring plus recorded-but-unpaid. */
    val outstandingTotal: Double = recurringTotal + unpaidTotal

    /** Part of [total] that comes from recurring templates (projected or already recorded). */
    val fromTemplatesTotal: Double = all.filter { it.templateId != null }.sumOf { it.amount }
}

/** Splits a tab's items into sections; order within each section is preserved. */
internal fun sectionsOf(items: List<ExpenseItem>): TabSections = TabSections(
    all = items,
    recurring = items.filter { it.isRecurring && !it.isPaid },
    unpaid = items.filter { !it.isRecurring && !it.isPaid },
    paid = items.filter { it.isPaid },
)
