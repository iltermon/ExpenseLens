package com.iltermon.expenselens.ui.util

import com.iltermon.expenselens.ui.ExpenseItem
import com.iltermon.sankey.SankeyEntry

/** Which dimension the Sankey diagram's output column is grouped by. */
enum class SankeyOutputMode { CATEGORY, ACCOUNT, COUNTERPARTY }

/** The two node columns for [com.iltermon.sankey.SankeyDiagram]: income sources → output targets. */
data class SankeyChartData(val incomes: List<SankeyEntry>, val outputs: List<SankeyEntry>)

private const val UNASSIGNED_ID = "__unassigned__"
private const val OTHER_ID = "__other__"

/** Income categories contributing less than this share of total income are folded into "Other". */
private const val INCOME_MIN_SHARE = 0.10

/**
 * Aggregate [items] into Sankey nodes. Semantics mirror `AnalyticsScreen.netOf` so the diagram
 * agrees with the spending cards:
 *
 * - **Income column** (all modes): non-expense items posted to an income-only category, grouped by
 *   category. Refunds and non-income-only incomes are *not* income here.
 * - **Output column**: expenses add; refund-incomes (non-expense items in an expense/"both"
 *   category) subtract, exactly as `netOf` treats them. Grouped by [mode]; buckets that net to
 *   zero or below are dropped.
 *
 * Each side is sorted by amount descending and folded to at most [maxNodesPerSide] entries, with the
 * tail collapsed into a single [otherLabel] node. Colors are left null so the library assigns them.
 */
fun buildSankeyData(
    items: List<ExpenseItem>,
    mode: SankeyOutputMode,
    incomeOnlyCategoryIds: Set<Int>,
    categoryNames: Map<Int, String>,
    accountNames: Map<Int, String>,
    counterpartyNames: Map<Int, String>,
    unassignedLabel: String,
    otherLabel: String,
    maxNodesPerSide: Int = 6,
): SankeyChartData {
    // --- Income side: real income only (income-only categories), grouped by category. ---
    val incomeTotals = LinkedHashMap<Int, Double>()
    for (item in items) {
        if (item.isExpense) continue
        val catId = item.categoryId ?: continue
        if (catId !in incomeOnlyCategoryIds) continue
        incomeTotals[catId] = (incomeTotals[catId] ?: 0.0) + item.amount
    }
    val incomes = incomeTotals
        .filter { it.value > 0.0 }
        .map { (catId, amount) ->
            SankeyEntry("c$catId", categoryNames[catId] ?: unassignedLabel, amount)
        }
    val incomeTotal = incomes.sumOf { it.amount }

    // --- Output side: net spend per bucket (expenses add, refunds subtract). ---
    val outputTotals = LinkedHashMap<String, Pair<String, Double>>() // key -> (label, net)
    for (item in items) {
        val isRefund = !item.isExpense && (item.categoryId == null || item.categoryId !in incomeOnlyCategoryIds)
        if (!item.isExpense && !isRefund) continue // real income belongs to the income column
        val signed = if (item.isExpense) item.amount else -item.amount

        val (key, label) = outputBucket(item, mode, categoryNames, accountNames, counterpartyNames, unassignedLabel)
        val prev = outputTotals[key]
        outputTotals[key] = label to ((prev?.second ?: 0.0) + signed)
    }
    val outputs = outputTotals
        .filter { it.value.second > 0.0 }
        .map { (key, labelAmount) -> SankeyEntry(key, labelAmount.first, labelAmount.second) }

    return SankeyChartData(
        incomes = foldTopN(
            incomes.sortedByDescending { it.amount }, maxNodesPerSide, otherLabel,
            minAmount = incomeTotal * INCOME_MIN_SHARE,
        ),
        outputs = foldTopN(outputs.sortedByDescending { it.amount }, maxNodesPerSide, otherLabel),
    )
}

/** Resolve an item's output-column bucket (a stable id + display label) for the active [mode]. */
private fun outputBucket(
    item: ExpenseItem,
    mode: SankeyOutputMode,
    categoryNames: Map<Int, String>,
    accountNames: Map<Int, String>,
    counterpartyNames: Map<Int, String>,
    unassignedLabel: String,
): Pair<String, String> {
    val (prefix, id, names) = when (mode) {
        SankeyOutputMode.CATEGORY -> Triple("c", item.categoryId, categoryNames)
        SankeyOutputMode.ACCOUNT -> Triple("a", item.accountId, accountNames)
        SankeyOutputMode.COUNTERPARTY -> Triple("p", item.counterpartyId, counterpartyNames)
    }
    return if (id == null) {
        UNASSIGNED_ID to unassignedLabel
    } else {
        "$prefix$id" to (names[id] ?: unassignedLabel)
    }
}

/**
 * Collapse the tail of a (descending-sorted) list into one [otherLabel] node. Entries below
 * [minAmount] are always folded, and the result is capped at [maxNodes] nodes (the smallest kept
 * ones fold too when there are more big entries than fit). Returns the list unchanged when nothing
 * needs folding.
 */
private fun foldTopN(
    entries: List<SankeyEntry>,
    maxNodes: Int,
    otherLabel: String,
    minAmount: Double = 0.0,
): List<SankeyEntry> {
    val bigCount = entries.count { it.amount >= minAmount }
    if (bigCount == entries.size && entries.size <= maxNodes) return entries
    val keepCount = minOf(bigCount, maxNodes - 1)
    val kept = entries.take(keepCount)
    val otherAmount = entries.drop(keepCount).sumOf { it.amount }
    return if (otherAmount > 0.0) kept + SankeyEntry(OTHER_ID, otherLabel, otherAmount) else kept
}
