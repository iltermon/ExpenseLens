package com.iltermon.expenselens.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.iltermon.expenselens.R

/**
 * Display helpers that turn the stable, English-keyed values persisted in the database
 * (account types, recurrence units) into the user's selected language. The stored value
 * never changes — only what is shown. Unknown values (e.g. legacy imported account types
 * like "Bank") fall back to the raw stored string.
 */

@Composable
fun accountTypeLabel(type: String): String = when (type) {
    "Debit" -> stringResource(R.string.account_type_debit)
    "Credit Card" -> stringResource(R.string.account_type_credit_card)
    "Investment" -> stringResource(R.string.account_type_investment)
    "Cash" -> stringResource(R.string.account_type_cash)
    "Savings" -> stringResource(R.string.account_type_savings)
    else -> type
}

@Composable
fun frequencyUnitLabel(unit: String): String = when (unit) {
    "Day" -> stringResource(R.string.unit_day)
    "Week" -> stringResource(R.string.unit_week)
    "Month" -> stringResource(R.string.unit_month)
    "Year" -> stringResource(R.string.unit_year)
    else -> unit
}

/** "Account Name (Type)" — used by the account dropdowns. */
@Composable
fun accountWithType(name: String, type: String): String =
    stringResource(R.string.account_with_type, name, accountTypeLabel(type))

/**
 * Localized recurrence label for a list row, e.g. "Monthly" or "Every 2 Months".
 * [interval] and [unit] come straight from the [com.iltermon.expenselens.data.RecurringTemplate].
 */
@Composable
fun frequencyLabel(interval: Int, unit: String): String =
    if (interval == 1) {
        when (unit) {
            "Day" -> stringResource(R.string.freq_daily)
            "Week" -> stringResource(R.string.freq_weekly)
            "Month" -> stringResource(R.string.freq_monthly)
            "Year" -> stringResource(R.string.freq_yearly)
            else -> unit
        }
    } else {
        val pluralUnit = when (unit) {
            "Day" -> stringResource(R.string.unit_plural_day)
            "Week" -> stringResource(R.string.unit_plural_week)
            "Month" -> stringResource(R.string.unit_plural_month)
            "Year" -> stringResource(R.string.unit_plural_year)
            else -> unit
        }
        stringResource(R.string.freq_every, interval, pluralUnit)
    }
