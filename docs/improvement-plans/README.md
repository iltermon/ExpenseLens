# Improvement Plans

Findings from a full-project review (2026-07-03) covering the data layer, ViewModel, UI layer, and
cross-cutting concerns, organized into four independently implementable phases. Line numbers refer
to the state of the code on branch `refactor/transaction-form-core-fields` at the time of review
and will drift — treat them as starting points, not gospel.

| Phase | File | Theme | Effort | Risk |
|-------|------|-------|--------|------|
| A | [phase-a-correctness.md](phase-a-correctness.md) | Correctness hardening + tests | Small | Low |
| B | [phase-b-conventions.md](phase-b-conventions.md) | Convention & shipping fixes | Small | Low |
| C | [phase-c-structure.md](phase-c-structure.md) | Structural refactors | Large | Medium |
| D | [phase-d-nice-to-have.md](phase-d-nice-to-have.md) | Deferred improvements | Medium | Medium |

**Recommended order: A → B → C → D.** Phase A's unit tests are the safety net for Phase C's
refactors; B is independent and can go any time; D items are individually optional.

## What the review found to be sound (no action needed)

- **Recurrence math** (`Recurrence.kt`): month-end handling is correct — occurrences are computed
  from the fixed anchor (`anchor.plusMonths(k*interval)`), so a Jan-31 monthly template clamps to
  Feb 28/29 and self-corrects to Mar 31. No drift, no off-by-one found.
- **Dedup** is by `(templateId, date)`, not description — renaming a template does not
  double-count.
- **Timezone handling**: forms use `LocalDate` throughout; pickers convert via `ZoneOffset.UTC` in
  both directions consistently. Round-trips are safe.
- **Auto-pay backfill** works: `occurrencesInRange(startDate, today)` fills every missed occurrence
  on next open (the *architecture* around it is Phase C's concern, not the math).
- **Localization files**: `values/` and `values-tr/` are in sync — 151/151 identical key sets.
- **Repository/DAOs**: repository is an appropriately thin passthrough; the four `withTransaction`
  blocks (deleteSeries, mergeCounterparties, deleteCounterparty, clear) are correctly placed; no
  `allowMainThreadQueries` anywhere.

## Severity-ranked findings index

| # | Sev | Finding | Phase |
|---|-----|---------|-------|
| 1 | HIGH | Auto-pay engine: reactive collector writes into the table it observes | C |
| 2 | HIGH | Unguarded `LocalDate.parse` can crash list/analytics flows | A |
| 3 | HIGH | Zero test coverage of recurrence/dedup logic | A |
| 4 | HIGH | God ViewModel: ~10 responsibilities, 529 lines | C |
| 5 | HIGH | Shared composables defined in screen-specific files | B |
| 6 | MED | `REPLACE` conflict strategy can clobber user-edited occurrences | A |
| 7 | MED | `frequencyUnit` "Monthly" vs "Month" vocabulary drift | A |
| 8 | MED | Dev Excel importer ships in release builds | B |
| 9 | MED | English display strings persisted to DB | B (partial) / D |
| 10 | MED | No FKs/indices; account deletes orphan references | D |
| 11 | MED | Near-duplicate screens (~200 redundant lines) | C |
| 12 | MED | Aggregation re-runs in composable bodies every recomposition | C |
| 13 | LOW | Stringly-typed navigation, flat ui/ package, dead code | A (dead code) / D |
