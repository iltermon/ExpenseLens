# Filters & Sorting for Transaction Lists

## Context

Filter and sort the Expenses/Income lists by all available transaction information (date excluded — the month steppers / range picker already cover it). Today the only filter is the date range; sorting is hard-fixed to date-ascending in `mergeItems`; the lists are partitioned into Recurring / To-be-paid / Paid sections.

Decisions made in discussion:
- **Basic tier** (always visible): text search (description + counterparty name) and a multi-select category `FilterChip` row.
- **Advanced tier**: `ModalBottomSheet` from a filter icon with a count badge — categories, accounts, counterparties (multi-select), amount min/max, paid status, recurrence, "no account"/"no counterparty" toggles. AND across dimensions, OR within a dimension. Removable active-filter summary chips + Clear all.
- **Sorting**: menu with Date / Amount / Description / Category / Counterparty; tap active key to flip direction. A non-default sort (anything but date-ascending) **flattens the sections** into one ranked list; sections return on default sort.
- **Independent per-tab state** (Expenses vs Income), held in the ViewModel as StateFlows like the existing `_dateRange`.
- SummaryBar totals reflect the filtered list (what you see is what the totals say) — this falls out naturally since screens sum the list they render.
- `analyticsItems` (Analytics tab) is **unaffected** by these filters.

## 1. Core logic — new `app/src/main/java/com/iltermon/expenselens/ui/ExpenseItemFiltering.kt`

Pure Kotlin, no Android imports (JVM unit-testable):

```kotlin
enum class PaidStatusFilter { ALL, PAID, UNPAID }
enum class RecurrenceFilter { ALL, RECURRING, ONE_TIME }
enum class SortKey { DATE, AMOUNT, DESCRIPTION, CATEGORY, COUNTERPARTY }

data class SortState(val key: SortKey = SortKey.DATE, val ascending: Boolean = true) {
    val isDefault get() = key == SortKey.DATE && ascending
    fun tapped(newKey: SortKey): SortState  // same key -> flip direction; new key -> ascending
}

data class ListFilterState(
    val searchQuery: String = "",
    val categories: Set<String> = emptySet(),     // ExpenseItem.category is a String name
    val accountIds: Set<Int> = emptySet(),
    val counterpartyIds: Set<Int> = emptySet(),
    val amountMin: Double? = null, val amountMax: Double? = null,
    val paidStatus: PaidStatusFilter = PaidStatusFilter.ALL,
    val recurrence: RecurrenceFilter = RecurrenceFilter.ALL,
    val noAccount: Boolean = false, val noCounterparty: Boolean = false,
) {
    val isActive get() = this != ListFilterState()
    val advancedCount: Int  // active dimensions counted once each — filter icon badge
}

fun applyFilterAndSort(
    items: List<ExpenseItem>, filter: ListFilterState, sort: SortState,
    counterpartyNamesById: Map<Int, String>,
): List<ExpenseItem>
```

Predicate semantics (AND across dimensions):
- **Search**: blank passes; else case-insensitive contains on `description` OR resolved counterparty name.
- **Categories**: empty passes; else `category in categories`.
- **Account** (OR within dimension): passes when `accountIds.isEmpty() && !noAccount`, or `accountId in accountIds`, or `noAccount && accountId == null`. Counterparty identical in shape.
- **Amount**: inclusive bounds, each side optional.
- **Paid**: ALL / `isPaid` / `!isPaid`.
- **Recurrence**: RECURRING → `templateId != null` (covers projected occurrences and auto-paid rows); ONE_TIME → `templateId == null`.

Sorting: comparator per key (DATE → ISO date string, lexicographic OK; AMOUNT; DESCRIPTION/CATEGORY lowercase; COUNTERPARTY resolved name lowercase with null-counterparty items last in **both** directions — primary "has no name" boolean, then name). Reverse only the key comparator when descending, then stable tie-breaks `.thenBy { it.date }.thenBy { it.id }`.

## 2. ViewModel — `ui/ExpenseLensViewModel.kt`

Insert after `expenseItems` (~line 255). Follows the file's existing flat-StateFlow style (`SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS)`):

```kotlin
private val _expensesFilter = MutableStateFlow(ListFilterState())   // + expensesFilter exposed
private val _expensesSort   = MutableStateFlow(SortState())          // + expensesSort exposed
private val _incomeFilter   = MutableStateFlow(ListFilterState())    // + incomeFilter exposed
private val _incomeSort     = MutableStateFlow(SortState())          // + incomeSort exposed

private fun tabItems(isExpense: Boolean, filter: StateFlow<ListFilterState>, sort: StateFlow<SortState>) =
    combine(expenseItems, filter, sort, counterparties) { items, f, s, cps ->
        applyFilterAndSort(items.filter { it.isExpense == isExpense }, f, s, cps.associate { it.id to it.name })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

val expensesTabItems = tabItems(true, _expensesFilter, _expensesSort)
val incomeTabItems   = tabItems(false, _incomeFilter, _incomeSort)

fun updateExpensesFilter(transform: (ListFilterState) -> ListFilterState)  // _expensesFilter.update
fun clearExpensesFilter(); fun setExpensesSort(key: SortKey)               // sort.update { it.tapped(key) }
// + income equivalents
```

Filters apply AFTER `mergeItems`, so recurring projection/dedup and `analyticsItems` are untouched. This also moves the `it.isExpense` partition out of the composables into the VM.

## 3. New UI files (shared composables in their own files under `ui/` — per project preference, not in screen files)

**`ui/FilterSortBar.kt`** — basic tier, mounted by each screen above its LazyColumn:
- Row 1: compact search `OutlinedTextField` (leading Search icon, trailing clear when non-blank, singleLine) + filter `IconButton` (`Icons.Default.FilterList` in `BadgedBox`, badge = `advancedCount`) + sort `IconButton` (`Icons.AutoMirrored.Filled.Sort`) anchoring the SortMenu. Icons-extended already a dependency.
- Row 2: `LazyRow` of category `FilterChip`s (multi-select, synced with the sheet's category section).
- Row 3 (only when advanced criteria active): removable `InputChip`s per active value (each account/counterparty/category, amount range as one chip, paid, recurrence, unassigned toggles) styled like TabScreenShell's `ActiveFilterBar` (primaryContainer + Close), plus "Clear all" `TextButton`.
- Also holds the small `NoFilterResults(onClearFilters)` empty-state composable.

**`ui/SortMenu.kt`** — `DropdownMenu`, five `DropdownMenuItem`s; the active item shows an ArrowUpward/ArrowDownward trailing icon; tapping active flips direction (via VM `tapped`).

**`ui/FilterBottomSheet.kt`** — Material3 `ModalBottomSheet` (first in the app; `rememberModalBottomSheetState(skipPartiallyExpanded = true)`, scrollable Column). **Live-apply** — every control mutates VM state immediately; no draft/apply step (list updating behind the sheet is good feedback, avoids duplicated state). Sections: Category chips (`FlowRow`), Account chips + "No account" chip, Counterparty chips + "No counterparty" chip, Amount min/max (`KeyboardType.Decimal`, `toDoubleOrNull()`), Status segmented row (All/Paid/Unpaid — `SingleChoiceSegmentedButtonRow`, pattern from AnalyticsScreen), Recurrence segmented row, bottom "Clear all" + "Done".

## 4. Screen changes

**`ui/ExpensesScreen.kt`** (mirror everything in **`ui/IncomeScreen.kt`** with income flows/accessors):
- Collect `expensesTabItems`, `expensesFilter`, `expensesSort`, `expenseCategories`, `accounts`, `counterparties`; keep `expenseItems` collected only to distinguish "no data this month" vs "filters match nothing".
- Replace `expenseItems.filter { it.isExpense }` (line 45) with `expensesTabItems`. Section partitions and TabScreenShell sums computed as today → totals are automatically filter-aware.
- Mount `FilterSortBar` as first child of the TabScreenShell content; local `showSheet` state gates `FilterBottomSheet`.
- **Flatten rule**: `if (sort.isDefault)` render the three sections as today, `else` one flat `items(items)` list without `SectionHeader`s.
- Empty state: if list empty and `filter.isActive` and the tab has underlying data → `NoFilterResults(viewModel::clearExpensesFilter)`; otherwise the existing empty text.

**`ui/TabScreenShell.kt`** — no changes; date-range `ActiveFilterBar` stays as is.

## 5. String resources (`values/strings.xml` + `values-tr/strings.xml`)

~30 new keys (EN / TR): `filter_search_placeholder` "Search description or counterparty"/"Açıklama veya karşı taraf ara"; icon cds `filter_open` Filters/Filtreler, `sort_open` Sort/Sırala; sheet sections `filter_section_category|account|counterparty|amount|status|recurrence` (Kategori, Hesap, Karşı Taraf, Tutar, Durum, Tekrarlama); `filter_amount_min|max` Min/En az, Max/En çok; status `filter_status_all|paid|unpaid` (Tümü, Ödenen, Ödenmemiş); recurrence `filter_recurrence_all|recurring|one_time` (Tümü, Tekrarlayan, Tek seferlik); `filter_no_account|no_counterparty` (Hesapsız, Karşı tarafsız); `filter_clear_all` Tümünü temizle; `filter_done` Tamam; `filter_no_results` "No transactions match your filters."/"Filtrelerinizle eşleşen işlem yok."; `filter_clear_filters` Filtreleri temizle; `filter_amount_range_chip` "%1$s – %2$s"; sort labels `sort_by_date|amount|description|category|counterparty`; cds `cd_sort_ascending|descending` Artan/Azalan. TR values to be confirmed during review.

Optional polish (flagged, not v1): income-specific Paid/Unpaid labels (Received/Pending) in the sheet; search field inside the counterparty section for large lists.

## 6. Ordered tasks (reviewable increments)

1. **Core logic + tests**: `ui/ExpenseItemFiltering.kt` + `app/src/test/.../ExpenseItemFilteringTest.kt` (plain JUnit4 — function is pure). Green tests, no UI change. `./gradlew testDebugUnitTest` (JAVA_HOME → JetBrains Runtime).
2. **VM wiring**: per-tab StateFlows, `tabItems` helper, mutators; switch both screens to the tab flows. App behaves identically (empty filters, default sort).
3. **Basic tier**: `FilterSortBar.kt` + `SortMenu.kt`, mount in both screens, flatten-on-sort rendering, `NoFilterResults`, first batch of strings EN+TR.
4. **Advanced tier**: `FilterBottomSheet.kt`, summary chips row, remaining strings.
5. **Polish + manual pass** (below).

## 7. Verification

**Unit tests** (`ExpenseItemFilteringTest.kt`): identity behavior (empty filter/default sort = date order); search by description and by counterparty name via map, null-counterparty items only match by description; multi-select OR within categories/accounts/counterparties; `noAccount` alone, ids alone, union of both; amount bounds inclusive; paid and recurrence variants (RECURRING includes projected occurrences and auto-paid rows); AND combination narrows; every sort key asc+desc; counterparty sort keeps null names last both directions; stable tie-breaks; `isDefault`, `tapped()` flip/switch, `advancedCount`.

**Manual on device** (confirm before any adb):
1. Expenses tab: search text → list and bottom SummaryBar totals shrink together; find a transaction by its store name when the description differs.
2. Toggle two category chips → OR between them, AND with the search.
3. Open the sheet: badge counts active dimensions; account picks + "No account" work as a union; amount min/max narrows; Paid/Unpaid and Recurring/One-time segmented rows work; unpaid recurring projections respect the filters.
4. Summary chips: removing one clears only that criterion; "Clear all" restores everything.
5. Sort by Amount → sections flatten to one ranked list; tap Amount again → flips; back to Date → sections reappear.
6. Income tab starts clean; set an Income filter, navigate away and back → per-tab state persists independently.
7. Zero-result filters → "No transactions match your filters" + working clear button; a genuinely empty month still shows the old empty text.
8. Analytics tab unchanged while Expenses filters active; month steppers/date range still work alongside the new filters.
9. Spot-check in Turkish locale.
