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
) {
    EXPENSE(isExpense = true, addTitle = R.string.add_expense_title),
    INCOME(isExpense = false, addTitle = R.string.add_income_title),
}
