package com.iltermon.expenselens.ui.transactions

import com.iltermon.expenselens.ui.ExpenseItem

/**
 * Pure, Android-free filtering and sorting for the Expenses/Income lists. Kept out of the ViewModel
 * so it is directly JVM-unit-testable. Operates on the already-merged [com.iltermon.expenselens.ui.ExpenseItem] list (concrete
 * transactions + projected recurring occurrences) produced by the ViewModel, layered on top of the
 * existing date-range filter — so the month/range picker and this stay independent.
 */

enum class PaidStatusFilter { ALL, PAID, UNPAID }

enum class RecurrenceFilter { ALL, RECURRING, ONE_TIME }

enum class SortKey { DATE, AMOUNT, DESCRIPTION, CATEGORY, COUNTERPARTY }

/**
 * The active sort. The default (date-ascending) is special: screens render their Recurring /
 * To-be-paid / Paid sections only while [isDefault]; any other sort flattens them into one ranked list.
 */
data class SortState(val key: SortKey = SortKey.DATE, val ascending: Boolean = true) {
    val isDefault: Boolean get() = key == SortKey.DATE && ascending

    /** Tapping the active key flips its direction; tapping a new key selects it ascending. */
    fun tapped(newKey: SortKey): SortState =
        if (newKey == key) copy(ascending = !ascending) else SortState(newKey, ascending = true)
}

/**
 * Everything the two tiers of the filter UI can constrain, beyond the date range. Empty everywhere is
 * the "no filter" state ([isActive] false). Categories/accounts/counterparties are keyed by id; the UI
 * resolves ids to names for display.
 */
data class ListFilterState(
    val searchQuery: String = "",
    val categoryIds: Set<Int> = emptySet(),
    val accountIds: Set<Int> = emptySet(),
    val counterpartyIds: Set<Int> = emptySet(),
    val amountMin: Double? = null,
    val amountMax: Double? = null,
    val paidStatus: PaidStatusFilter = PaidStatusFilter.ALL,
    val recurrence: RecurrenceFilter = RecurrenceFilter.ALL,
    val noAccount: Boolean = false,
    val noCounterparty: Boolean = false,
) {
    val isActive: Boolean get() = this != ListFilterState()

    /**
     * Number of *advanced* dimensions currently constraining the list — the badge on the filter icon.
     * The basic-tier search and category chips are excluded so the badge reflects only what's hidden
     * inside the sheet. Each dimension counts once regardless of how many values it holds.
     */
    val advancedCount: Int
        get() {
            var n = 0
            if (accountIds.isNotEmpty()) n++
            if (counterpartyIds.isNotEmpty()) n++
            if (amountMin != null || amountMax != null) n++
            if (paidStatus != PaidStatusFilter.ALL) n++
            if (recurrence != RecurrenceFilter.ALL) n++
            if (noAccount) n++
            if (noCounterparty) n++
            return n
        }
}

/**
 * Applies [filter] then [sort] to [items]. AND across dimensions, OR within a multi-value dimension.
 * [counterpartyNamesById]/[categoryNamesById] resolve ids for search and for the name-based sorts.
 */
/**
 * Whether a row with these fields passes the entity-shaped dimensions of this filter — category,
 * account, counterparty and amount. Excludes search, paid status and recurrence, so it is reusable for
 * rows that lack those concepts (e.g. recurring templates). Same OR-within / AND-across semantics as
 * [applyFilterAndSort].
 */
internal fun ListFilterState.matchesFields(
    categoryId: Int?,
    accountId: Int?,
    counterpartyId: Int?,
    amount: Double,
): Boolean {
    if (categoryIds.isNotEmpty() && categoryId !in categoryIds) return false
    if (!(accountIds.isEmpty() && !noAccount) &&
        !(accountId in accountIds || (noAccount && accountId == null))
    ) return false
    if (!(counterpartyIds.isEmpty() && !noCounterparty) &&
        !(counterpartyId in counterpartyIds || (noCounterparty && counterpartyId == null))
    ) return false
    if (amountMin != null && amount < amountMin) return false
    if (amountMax != null && amount > amountMax) return false
    return true
}

fun applyFilterAndSort(
    items: List<ExpenseItem>,
    filter: ListFilterState,
    sort: SortState,
    counterpartyNamesById: Map<Int, String>,
    categoryNamesById: Map<Int, String>,
): List<ExpenseItem> {
    val query = filter.searchQuery.trim()

    val filtered = items.filter { item ->
        matchesSearch(item, query, counterpartyNamesById) &&
            matchesCategory(item, filter) &&
            matchesAccount(item, filter) &&
            matchesCounterparty(item, filter) &&
            matchesAmount(item, filter) &&
            matchesPaid(item, filter) &&
            matchesRecurrence(item, filter)
    }

    return filtered.sortedWith(comparatorFor(sort, counterpartyNamesById, categoryNamesById))
}

private fun matchesSearch(item: ExpenseItem, query: String, cpNames: Map<Int, String>): Boolean {
    if (query.isEmpty()) return true
    if (item.description.contains(query, ignoreCase = true)) return true
    val cpName = item.counterpartyId?.let { cpNames[it] }
    return cpName?.contains(query, ignoreCase = true) == true
}

private fun matchesCategory(item: ExpenseItem, f: ListFilterState): Boolean =
    f.categoryIds.isEmpty() || item.categoryId in f.categoryIds

// Account / counterparty share a shape: no constraint passes everything; otherwise an item passes if
// its id is selected, OR the "unassigned" toggle is on and the item has no id.
private fun matchesAccount(item: ExpenseItem, f: ListFilterState): Boolean {
    if (f.accountIds.isEmpty() && !f.noAccount) return true
    return item.accountId in f.accountIds || (f.noAccount && item.accountId == null)
}

private fun matchesCounterparty(item: ExpenseItem, f: ListFilterState): Boolean {
    if (f.counterpartyIds.isEmpty() && !f.noCounterparty) return true
    return item.counterpartyId in f.counterpartyIds || (f.noCounterparty && item.counterpartyId == null)
}

private fun matchesAmount(item: ExpenseItem, f: ListFilterState): Boolean {
    if (f.amountMin != null && item.amount < f.amountMin) return false
    if (f.amountMax != null && item.amount > f.amountMax) return false
    return true
}

private fun matchesPaid(item: ExpenseItem, f: ListFilterState): Boolean = when (f.paidStatus) {
    PaidStatusFilter.ALL -> true
    PaidStatusFilter.PAID -> item.isPaid
    PaidStatusFilter.UNPAID -> !item.isPaid
}

// A recurring item is anything tied to a template — both projected occurrences and auto-paid rows.
private fun matchesRecurrence(item: ExpenseItem, f: ListFilterState): Boolean = when (f.recurrence) {
    RecurrenceFilter.ALL -> true
    RecurrenceFilter.RECURRING -> item.templateId != null
    RecurrenceFilter.ONE_TIME -> item.templateId == null
}

private fun comparatorFor(
    sort: SortState,
    cpNames: Map<Int, String>,
    catNames: Map<Int, String>,
): Comparator<ExpenseItem> {
    fun <T : Comparable<T>> directedBy(selector: (ExpenseItem) -> T): Comparator<ExpenseItem> =
        compareBy(selector).let { if (sort.ascending) it else it.reversed() }

    val directed: Comparator<ExpenseItem> = when (sort.key) {
        SortKey.DATE -> directedBy { it.date }
        SortKey.AMOUNT -> directedBy { it.amount }
        SortKey.DESCRIPTION -> directedBy { it.description.lowercase() }
        SortKey.CATEGORY -> directedBy { it.categoryId?.let { id -> catNames[id] }?.lowercase() ?: "" }
        // Items without a counterparty name sort last in BOTH directions: the "has no name" flag is a
        // fixed primary key (never reversed); only the name comparator flips with direction.
        SortKey.COUNTERPARTY -> {
            val nameOf = { item: ExpenseItem -> item.counterpartyId?.let { cpNames[it] }?.takeIf { it.isNotBlank() } }
            val byName = compareBy<ExpenseItem> { nameOf(it)?.lowercase() ?: "" }
            compareBy<ExpenseItem> { nameOf(it) == null }
                .then(if (sort.ascending) byName else byName.reversed())
        }
    }

    // Stable, direction-independent tie-breaks so equal keys keep a deterministic order.
    return directed.thenBy { it.date }.thenBy { it.id }
}
