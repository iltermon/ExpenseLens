package com.iltermon.expenselens.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionItemFilteringTest {

    // --- fixtures -----------------------------------------------------------------------------

    private fun item(
        id: Int,
        description: String = "item$id",
        amount: Double = 10.0,
        categoryId: Int? = null,
        date: String = "2026-07-01",
        isExpense: Boolean = true,
        isPaid: Boolean = false,
        isRecurring: Boolean = false,
        templateId: Int? = null,
        accountId: Int? = null,
        counterpartyId: Int? = null,
    ) = ExpenseItem(
        id = id,
        description = description,
        amount = amount,
        categoryId = categoryId,
        date = date,
        isExpense = isExpense,
        isPaid = isPaid,
        isRecurring = isRecurring,
        templateId = templateId,
        accountId = accountId,
        counterpartyId = counterpartyId,
    )

    private val cpNames = mapOf(1 to "Aldi", 2 to "Rewe", 3 to "Zzz Corp")
    private val catNames = mapOf(10 to "Groceries", 20 to "Rent", 30 to "Salary")

    private fun run(
        items: List<ExpenseItem>,
        filter: ListFilterState = ListFilterState(),
        sort: SortState = SortState(),
    ) = applyFilterAndSort(items, filter, sort, cpNames, catNames)

    private fun ids(items: List<ExpenseItem>) = items.map { it.id }

    // --- identity / default -------------------------------------------------------------------

    @Test
    fun emptyFilterDefaultSort_ordersByDateThenId_stably() {
        val items = listOf(
            item(3, date = "2026-07-05"),
            item(1, date = "2026-07-01"),
            item(2, date = "2026-07-01"),
        )
        assertEquals(listOf(1, 2, 3), ids(run(items)))
    }

    @Test
    fun isActive_isFalseForDefaultAndTrueOtherwise() {
        assertFalse(ListFilterState().isActive)
        assertTrue(ListFilterState(searchQuery = "a").isActive)
        assertTrue(ListFilterState(noAccount = true).isActive)
    }

    // --- search -------------------------------------------------------------------------------

    @Test
    fun search_matchesDescriptionCaseInsensitively() {
        val items = listOf(item(1, description = "Weekly SHOP"), item(2, description = "Fuel"))
        assertEquals(listOf(1), ids(run(items, ListFilterState(searchQuery = "shop"))))
    }

    @Test
    fun search_matchesCounterpartyNameViaMap() {
        val items = listOf(item(1, counterpartyId = 1), item(2, counterpartyId = 2))
        assertEquals(listOf(1), ids(run(items, ListFilterState(searchQuery = "aldi"))))
    }

    @Test
    fun search_nullCounterpartyMatchesOnlyByDescription() {
        val items = listOf(item(1, description = "no cp", counterpartyId = null))
        assertTrue(run(items, ListFilterState(searchQuery = "aldi")).isEmpty())
        assertEquals(listOf(1), ids(run(items, ListFilterState(searchQuery = "no cp"))))
    }

    // --- multi-value OR within a dimension ----------------------------------------------------

    @Test
    fun categories_orWithinDimension() {
        val items = listOf(item(1, categoryId = 10), item(2, categoryId = 20), item(3, categoryId = 30))
        val out = run(items, ListFilterState(categoryIds = setOf(10, 30)))
        assertEquals(listOf(1, 3), ids(out))
    }

    @Test
    fun accounts_idsOnly_noAccountOnly_andUnion() {
        val items = listOf(
            item(1, accountId = 100),
            item(2, accountId = 200),
            item(3, accountId = null),
        )
        assertEquals(listOf(1), ids(run(items, ListFilterState(accountIds = setOf(100)))))
        assertEquals(listOf(3), ids(run(items, ListFilterState(noAccount = true))))
        assertEquals(listOf(1, 3), ids(run(items, ListFilterState(accountIds = setOf(100), noAccount = true))))
    }

    @Test
    fun counterparties_unassignedToggle() {
        val items = listOf(item(1, counterpartyId = 1), item(2, counterpartyId = null))
        assertEquals(listOf(2), ids(run(items, ListFilterState(noCounterparty = true))))
    }

    // --- amount -------------------------------------------------------------------------------

    @Test
    fun amount_boundsAreInclusiveAndOptional() {
        val items = listOf(item(1, amount = 5.0), item(2, amount = 10.0), item(3, amount = 15.0))
        assertEquals(listOf(2, 3), ids(run(items, ListFilterState(amountMin = 10.0))))
        assertEquals(listOf(1, 2), ids(run(items, ListFilterState(amountMax = 10.0))))
        assertEquals(listOf(2), ids(run(items, ListFilterState(amountMin = 10.0, amountMax = 10.0))))
    }

    // --- paid / recurrence --------------------------------------------------------------------

    @Test
    fun paidStatus_variants() {
        val items = listOf(item(1, isPaid = true), item(2, isPaid = false))
        assertEquals(listOf(1), ids(run(items, ListFilterState(paidStatus = PaidStatusFilter.PAID))))
        assertEquals(listOf(2), ids(run(items, ListFilterState(paidStatus = PaidStatusFilter.UNPAID))))
    }

    @Test
    fun recurrence_recurringIncludesProjectedAndAutoPaidRows() {
        val projected = item(1, isRecurring = true, templateId = 5, isPaid = false)
        val autoPaid = item(2, isRecurring = false, templateId = 5, isPaid = true) // materialized auto-payment
        val oneTime = item(3, templateId = null)
        assertEquals(listOf(1, 2), ids(run(listOf(projected, autoPaid, oneTime), ListFilterState(recurrence = RecurrenceFilter.RECURRING))))
        assertEquals(listOf(3), ids(run(listOf(projected, autoPaid, oneTime), ListFilterState(recurrence = RecurrenceFilter.ONE_TIME))))
    }

    // --- AND across dimensions ----------------------------------------------------------------

    @Test
    fun dimensionsCombineWithAnd() {
        val items = listOf(
            item(1, description = "shop", categoryId = 10, amount = 50.0),
            item(2, description = "shop", categoryId = 20, amount = 50.0),
            item(3, description = "fuel", categoryId = 10, amount = 50.0),
        )
        val out = run(items, ListFilterState(searchQuery = "shop", categoryIds = setOf(10), amountMin = 40.0))
        assertEquals(listOf(1), ids(out))
    }

    // --- sorting ------------------------------------------------------------------------------

    @Test
    fun sortByAmount_ascAndDesc() {
        val items = listOf(item(1, amount = 30.0), item(2, amount = 10.0), item(3, amount = 20.0))
        assertEquals(listOf(2, 3, 1), ids(run(items, sort = SortState(SortKey.AMOUNT, ascending = true))))
        assertEquals(listOf(1, 3, 2), ids(run(items, sort = SortState(SortKey.AMOUNT, ascending = false))))
    }

    @Test
    fun sortByDescription_caseInsensitive() {
        val items = listOf(item(1, description = "banana"), item(2, description = "Apple"), item(3, description = "cherry"))
        assertEquals(listOf(2, 1, 3), ids(run(items, sort = SortState(SortKey.DESCRIPTION, ascending = true))))
    }

    @Test
    fun sortByCategory_usesResolvedNames() {
        val items = listOf(item(1, categoryId = 20), item(2, categoryId = 10)) // Rent, Groceries
        assertEquals(listOf(2, 1), ids(run(items, sort = SortState(SortKey.CATEGORY, ascending = true))))
    }

    @Test
    fun sortByCounterparty_nullNamesLastInBothDirections() {
        val items = listOf(
            item(1, counterpartyId = 2, date = "2026-07-01"),  // Rewe
            item(2, counterpartyId = null, date = "2026-07-01"),
            item(3, counterpartyId = 1, date = "2026-07-01"),  // Aldi
        )
        // Ascending: Aldi, Rewe, then the null-name item last.
        assertEquals(listOf(3, 1, 2), ids(run(items, sort = SortState(SortKey.COUNTERPARTY, ascending = true))))
        // Descending: Rewe, Aldi, and the null-name item STILL last.
        assertEquals(listOf(1, 3, 2), ids(run(items, sort = SortState(SortKey.COUNTERPARTY, ascending = false))))
    }

    @Test
    fun sort_tieBreaksAreDeterministicByDateThenId() {
        val items = listOf(
            item(3, amount = 10.0, date = "2026-07-03"),
            item(1, amount = 10.0, date = "2026-07-01"),
            item(2, amount = 10.0, date = "2026-07-01"),
        )
        // Equal amounts -> ordered by date then id, regardless of direction of the (equal) key.
        assertEquals(listOf(1, 2, 3), ids(run(items, sort = SortState(SortKey.AMOUNT, ascending = false))))
    }

    // --- SortState / advancedCount ------------------------------------------------------------

    @Test
    fun sortState_tappedFlipsOrSwitches() {
        val date = SortState()
        assertTrue(date.isDefault)
        assertEquals(SortState(SortKey.DATE, ascending = false), date.tapped(SortKey.DATE))
        assertEquals(SortState(SortKey.AMOUNT, ascending = true), date.tapped(SortKey.AMOUNT))
        // switching keys always resets to ascending, even from a descending state
        assertEquals(
            SortState(SortKey.CATEGORY, ascending = true),
            SortState(SortKey.AMOUNT, ascending = false).tapped(SortKey.CATEGORY),
        )
    }

    @Test
    fun advancedCount_countsEachActiveAdvancedDimensionOnce_excludingSearchAndCategories() {
        assertEquals(0, ListFilterState().advancedCount)
        // search + category chips are basic tier -> not counted
        assertEquals(0, ListFilterState(searchQuery = "x", categoryIds = setOf(1, 2)).advancedCount)
        val f = ListFilterState(
            accountIds = setOf(1, 2),          // 1
            counterpartyIds = setOf(3),        // 1
            amountMin = 5.0,                   // 1 (range counts once)
            paidStatus = PaidStatusFilter.PAID,// 1
            recurrence = RecurrenceFilter.ONE_TIME, // 1
            noAccount = true,                  // 1
            noCounterparty = true,             // 1
        )
        assertEquals(7, f.advancedCount)
    }
}
