package com.iltermon.expenselens.ui.util

import com.iltermon.expenselens.ui.ExpenseItem
import com.iltermon.sankey.SankeyEntry

/** Which dimension the Sankey diagram's output column is grouped by. */
enum class SankeyOutputMode { CATEGORY, ACCOUNT, COUNTERPARTY }

/** The two node columns for [com.iltermon.sankey.SankeyDiagram]: income sources → output targets. */
data class SankeyChartData(val incomes: List<SankeyEntry>, val outputs: List<SankeyEntry>)

private const val UNASSIGNED_ID = "__unassigned__"
private const val OTHER_ID = "__other__"
private const val INCOME_ID_PREFIX = "in:"

/** Income categories contributing less than this share of total income are folded into "Other". */
private const val INCOME_MIN_SHARE = 0.10

/**
 * Aggregate [items] into Sankey nodes. Totals use the same definition as the Analytics summary card,
 * so the diagram's two sides always sum to the card's "Income" and "Expenses":
 *
 * - **Income column** (all modes): every non-expense item, grouped by category. Income in an
 *   expense/"both" category (e.g. a refund) is still income here.
 * - **Output column**: every expense, grouped by [mode].
 *
 * Each side is sorted by amount descending and folded to at most [maxNodesPerSide] entries, with the
 * tail collapsed into a single [otherLabel] node. Colors are left null so the library assigns them.
 */
fun buildSankeyData(
    items: List<ExpenseItem>,
    mode: SankeyOutputMode,
    categoryNames: Map<Int, String>,
    accountNames: Map<Int, String>,
    counterpartyNames: Map<Int, String>,
    unassignedLabel: String,
    otherLabel: String,
    maxNodesPerSide: Int = 6,
): SankeyChartData {
    // --- Income side: all income, grouped by category. ---
    val incomeTotals = LinkedHashMap<String, Pair<String, Double>>() // key -> (label, total)
    for (item in items) {
        if (item.isExpense) continue
        val (key, label) = outputBucket(item, SankeyOutputMode.CATEGORY, categoryNames, accountNames, counterpartyNames, unassignedLabel)
        incomeTotals[key] = label to ((incomeTotals[key]?.second ?: 0.0) + item.amount)
    }
    val incomes = incomeTotals
        .filter { it.value.second > 0.0 }
        .map { (key, labelAmount) -> SankeyEntry(key, labelAmount.first, labelAmount.second) }
    val incomeTotal = incomes.sumOf { it.amount }

    // --- Output side: all expenses, grouped by the active mode. ---
    val outputTotals = LinkedHashMap<String, Pair<String, Double>>() // key -> (label, total)
    for (item in items) {
        if (!item.isExpense) continue
        val (key, label) = outputBucket(item, mode, categoryNames, accountNames, counterpartyNames, unassignedLabel)
        outputTotals[key] = label to ((outputTotals[key]?.second ?: 0.0) + item.amount)
    }
    val outputs = outputTotals
        .filter { it.value.second > 0.0 }
        .map { (key, labelAmount) -> SankeyEntry(key, labelAmount.first, labelAmount.second) }

    return SankeyChartData(
        // The diagram resolves both sides through one id map, so income ids get their own prefix:
        // a "both" category (or "Other"/"Unassigned") can appear on each side.
        incomes = foldTopN(
            incomes.sortedByDescending { it.amount }, maxNodesPerSide, otherLabel,
            minAmount = incomeTotal * INCOME_MIN_SHARE,
        ).map { it.copy(id = INCOME_ID_PREFIX + it.id) },
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
