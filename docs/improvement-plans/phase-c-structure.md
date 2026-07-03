# Phase C — Structural Refactors

The big one. **Prerequisite: Phase A merged** (its recurrence tests are the safety net here).
Sequence the four steps in order — each leaves the app working; don't attempt them as one change.

## C1. Extract the auto-pay engine from the reactive collector (HIGH)

**Problem** (`ExpenseLensViewModel.kt:77-119`): an `init`-launched `combine(getAllTemplates(),
getAllTransactions()).collect { ... }` that *writes transactions inside the collector observing
transactions*. Consequences:
- Every insert re-fires the combine → re-expands every template (O(n) refires on bulk import).
- A second full-table read per emission (`repository.getAllTransactions().first()` at :92).
- Loop protection rests solely on the in-memory `existingKeys` HashSet at :101.
- Runs forever on `viewModelScope`, not tied to any subscriber.

**Change:** replace with an explicit one-shot backfill:

```kotlin
class AutoPayBackfill(private val repository: ExpenseLensRepository) {
    suspend fun run(today: LocalDate = LocalDate.now()) {
        val templates = repository.getAllTemplates().first()
        val existing = repository.getAllTransactions().first()
            .mapNotNullTo(HashSet()) { t -> t.templateId?.let { it to t.date } }
        for (template in templates.filter { it.autoPayment }) {
            for (date in template.occurrencesInRange(template.startLocalDate, today)) {
                val key = template.id to date.toString()
                if (key !in existing) repository.insertGeneratedTransaction(/* ... */)
            }
        }
    }
}
```

Trigger it: on ViewModel creation (once), and optionally on `ON_RESUME` / date change for
long-lived processes. With Phase A2's `IGNORE` insert, even a double-run is harmless.
Delete the `init` collector.

**Test:** extend Phase A's tests — backfill twice, assert no duplicates; edit a generated row,
re-run, assert edit survives.

## C2. Split the god ViewModel (HIGH)

**Problem:** `ExpenseLensViewModel.kt` is 529 lines, ~35 public members, ~10 responsibilities.

**Seams (in extraction order, easiest first):**

1. **Import** (`importFromUri`, `clearTransactions`, `_importStatus` — :452-490) →
   `ImportUseCase`. Also unlocks Phase B2's cleaner source-set separation.
2. **Counterparty resolution** (`resolveCounterparty`, `saveTransaction`/`updateTransaction`/
   `saveTemplate`/`updateTemplate` choice-variants, `mergeCounterparties` — :344-410) →
   `CounterpartySaveUseCase`. The forms already speak `CounterpartyChoice`; the VM methods become
   thin delegations. Fix the `getCounterpartyByName(choice.name)!!` NPE-under-race at :384 while
   extracting (fall back to re-insert or return null).
3. **Auto-pay** → C1's `AutoPayBackfill`.
4. **Analytics state** (`_analyticsPeriod`, `_analyticsYear`, `analyticsDateRange`,
   `analyticsFilteredTransactions`, `analyticsItems`, `setAnalyticsPeriod/Previous/Next` —
   :259-312) → `AnalyticsViewModel` with the use-cases injected.

Keep `ExpenseLensViewModel` as the shared screen VM: month/date-range navigation, the entity list
StateFlows, and thin delegations. Construction: manual factory in `MainActivity`/`AppNavigation`
(pattern already used); no DI framework needed at this size.

## C3. Hoist per-screen aggregation out of composable bodies (MED)

**Problem:** filter/sum chains run on every recomposition:
- `ExpensesScreen.kt:45-60` — 4 filters + 5 sums over `expenseItems`
- `IncomeScreen.kt:32-47` — same pattern
- `AnalyticsScreen.kt:68-97` — O(categories × items) `categoryRows` + O(accounts × items)
  `accountRows`

**Change:** expose derived StateFlows from the (post-C2) ViewModels, e.g. a
`data class ExpenseSummary(recurring, unpaid, paid, totals...)` computed once per emission via
`.map{}.stateIn()`. Composables just `collectAsState()` and render. Interim cheap fix if done
before C2: wrap in `remember(items) { ... }`.

Also consider pushing period filtering into SQL (`WHERE date BETWEEN :start AND :end`) —
`getTransactionsByMonth` was the dead attempt at this (removed in A5); do it properly with a
range-parameterized DAO query feeding `filteredTransactions`.

## C4. Collapse near-duplicate screens (MED)

| Pair | Duplication | Collapse to |
|---|---|---|
| `AddExpenseScreen.kt` / `AddIncomeScreen.kt` | ~95% (80 lines each; differ only in `isExpense`, category list, title) | `AddTransactionScreen(isExpense: Boolean)` |
| `ExpensesScreen.kt` / `IncomeScreen.kt` | same shell + sectioned LazyColumn skeleton | `TransactionListScreen(isExpense: Boolean)` (labels via resources) |
| `EditTransactionScreen.kt` / `EditTemplateScreen.kt` | identical prefill scaffolding (two LaunchedEffects, `prefilled` flag, spinner) | shared `EditEntityScaffold` slot composable; each screen keeps only its form + load/save wiring |

~200 lines removed. Update `AppNavigation.kt` routes to pass `isExpense` (route arg or two routes
into one composable). Do this *after* C2/C3 so the screens being merged are already thin.

## Verification

After each step: `./gradlew test assembleDebug lint` green.

End-to-end manual pass:
- Fresh install → recurring template with auto-pay → occurrences appear once; restart → no dupes.
- Bulk import (debug) → no O(n) re-expansion stalls; totals correct.
- All four tab screens render identical totals to pre-refactor (screenshot-compare if unsure).
- Add + edit both a transaction and a template through the collapsed screens; counterparty
  defaults prompt still works.
- Custom date-range filter still works on both list screens (post-C4 single implementation).
