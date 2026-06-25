package com.iltermon.expenselens.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * The currency symbol the user picked in Settings, provided once near the root (see MainActivity)
 * and read anywhere an amount is shown. Display only — amounts are stored without a currency, so
 * changing this never touches transaction data. Independent of the app language.
 */
val LocalCurrencySymbol = compositionLocalOf { "€" }

/** Formats an amount with the active currency symbol, e.g. "€12.50". */
@Composable
fun money(amount: Double, decimals: Int = 2): String =
    LocalCurrencySymbol.current + "%.${decimals}f".format(amount)
