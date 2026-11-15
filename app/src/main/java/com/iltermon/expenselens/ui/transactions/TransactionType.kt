package com.iltermon.expenselens.ui.transactions

import androidx.annotation.StringRes
import com.iltermon.expenselens.R

/**
 * Which kind of transaction a shared screen shows, carrying everything that differs between the
 * two so screens read `type.addTitle` instead of branching on a Boolean. Screen-level only: the
 * data model and the forms keep `isExpense`.
 */
enum class TransactionType(
    val isExpense: Boolean,
    @StringRes val addTitle: Int,
    @StringRes val listTitle: Int,
    @StringRes val totalLabel: Int,
    @StringRes val outstandingLabel: Int,
    @StringRes val emptyText: Int,
    @StringRes val unpaidSection: Int,
    @StringRes val paidSection: Int,
) {
    EXPENSE(
        isExpense = true,
        addTitle = R.string.add_expense_title,
        listTitle = R.string.nav_expenses,
        totalLabel = R.string.expenses_this_month,
        outstandingLabel = R.string.expenses_remaining_payment,
        emptyText = R.string.no_expenses_for_period,
        unpaidSection = R.string.section_to_be_paid,
        paidSection = R.string.section_paid,
    ),
    INCOME(
        isExpense = false,
        addTitle = R.string.add_income_title,
        listTitle = R.string.nav_income,
        totalLabel = R.string.income_this_month,
        outstandingLabel = R.string.income_pending,
        emptyText = R.string.no_income_for_period,
        unpaidSection = R.string.section_pending,
        paidSection = R.string.section_received,
    ),
}
