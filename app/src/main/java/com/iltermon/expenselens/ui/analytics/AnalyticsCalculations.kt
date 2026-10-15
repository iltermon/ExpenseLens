package com.iltermon.expenselens.ui.analytics

import com.iltermon.expenselens.data.Account
import com.iltermon.expenselens.data.Category
import com.iltermon.expenselens.data.Counterparty
import com.iltermon.expenselens.ui.ExpenseItem

// Pure analytics math, computed once per data change in AnalyticsViewModel instead of on every
// recomposition of AnalyticsScreen. Free of Android types so it is unit-testable.

/** Gross totals for the summary bar and cards (same definition the Sankey graph uses). */
data class AnalyticsSummary(
    val totalExpenses: Double = 0.0,
    val totalIncome: Double = 0.0,
    val recurringExpenses: Double = 0.0,
    val recurringIncome: Double = 0.0,
) {
    /** Net balance from the user's perspective: positive when income outpaces expenses. */
    val net: Double get() = totalIncome - totalExpenses
}

/** One row of a spending breakdown: net spend, optional recurring portion, optional budget limit. */
data class SpendRow(
    val name: String,
    val net: Double,
    val recurring: Double?,
    val limit: Double?
)

data class AnalyticsBreakdown(
    val categoryRows: List<SpendRow> = emptyList(),
    val accountRows: List<SpendRow> = emptyList(),
    val counterpartyRows: List<SpendRow> = emptyList(),
)

internal fun summarize(items: List<ExpenseItem>): AnalyticsSummary = AnalyticsSummary(
    totalExpenses = items.filter { it.isExpense }.sumOf { it.amount },
    totalIncome = items.filter { !it.isExpense }.sumOf { it.amount },
    recurringExpenses = items.filter { it.isExpense && it.templateId != null }.sumOf { it.amount },
    recurringIncome = items.filter { !it.isExpense && it.templateId != null }.sumOf { it.amount },
)

/**
 * Net spending per category, account and counterparty for the period. Limits are the monthly or
 * yearly ones depending on [isMonth]. Each list is sorted by net spend, descending.
 */
internal fun buildBreakdown(
    items: List<ExpenseItem>,
    categories: List<Category>,
    accounts: List<Account>,
    counterparties: List<Counterparty>,
    isMonth: Boolean,
): AnalyticsBreakdown {
    // Income-only categories are real income (salary, etc.), not returns — they must not reduce spend.
    val incomeOnlyCategoryIds = categories.filter { it.type == "income" }.map { it.id }.toSet()
    val byCategory = items.groupBy { it.categoryId }
    val byAccount = items.groupBy { it.accountId }
    val byCounterparty = items.groupBy { it.counterpartyId }

    // Keep any category that was spent on OR has a limit set (mirrors the spreadsheet's Monthly Summary).
    val categoryRows = categories.mapNotNull { cat ->
        val limit = if (isMonth) cat.limitMonthly else cat.limitYearly
        val net = netOf(byCategory[cat.id].orEmpty(), incomeOnlyCategoryIds)
        if (net > 0 || limit != null) SpendRow(cat.name, net, null, limit) else null
    }.sortedByDescending { it.net }

    // Net spending per account, plus the recurring portion (mirrors the by-account table).
    val accountRows = accounts.mapNotNull { acc ->
        val limit = if (isMonth) acc.limitMonthly else acc.limitYearly
        val accItems = byAccount[acc.id].orEmpty()
        val net = netOf(accItems, incomeOnlyCategoryIds)
        val recurring = netOf(accItems.filter { it.templateId != null }, incomeOnlyCategoryIds)
        if (net != 0.0 || recurring != 0.0 || limit != null) SpendRow(acc.name, net, recurring, limit) else null
    }.sortedByDescending { it.net }

    // Counterparties (stores/vendors) carry no limit or recurring portion.
    val counterpartyRows = counterparties.mapNotNull { cp ->
        val net = netOf(byCounterparty[cp.id].orEmpty(), incomeOnlyCategoryIds)
        if (net > 0) SpendRow(cp.name, net, null, null) else null
    }.sortedByDescending { it.net }

    return AnalyticsBreakdown(categoryRows, accountRows, counterpartyRows)
}

/**
 * Net spending for a set of items: expenses add; income subtracts **only** when it's a refund —
 * i.e. recorded into an expense/"both" category. Income posted to an income-only category (salary,
 * etc.) is real income, not a return, so it's ignored and never reduces spending.
 */
private fun netOf(items: List<ExpenseItem>, incomeOnlyCategoryIds: Set<Int>): Double =
    items.sumOf {
        when {
            it.isExpense -> it.amount
            it.categoryId in incomeOnlyCategoryIds -> 0.0
            else -> -it.amount
        }
    }
