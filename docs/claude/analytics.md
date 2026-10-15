# Analytics & Sankey

## Where it lives

`AnalyticsViewModel` builds the period's items and derives `summary` and `breakdown` once per data
change; the math is pure in `AnalyticsCalculations.kt` (`summarize`, `buildBreakdown`, `netOf`,
tested in `AnalyticsCalculationsTest`). `AnalyticsScreen` only renders. Month mode follows the
shared `PeriodSelection` (see [ui.md](ui.md)).

## Summary totals

- **Income** = sum of every non-expense item. **Expenses** = sum of every expense item.
  This is the single definition of "income" used by the summary card *and* the Sankey graph.
- Category/account/counterparty breakdown rows use `netOf`, which subtracts refunds (income in an
  expense or "both" category) from spending. These rows can therefore show less than the matching
  Sankey output node when refunds exist. This is a known, open product question.

## Sankey mapping (`ui/util/SankeyMapper.kt` → `buildSankeyData`)

- Income column: all income, grouped by category. Output column: all expenses, grouped by the
  selected mode (category / account / counterparty).
- Each side is sorted descending and folded into "Other" at `maxNodesPerSide`. Income categories
  under 10% of total income are folded too. Folding preserves side totals.
- Income node ids get the prefix `in:`. `SankeyDiagram` resolves both sides through **one** id map,
  so ids must be unique across sides (a "both" category or "Other" can appear on each). The forms
  require a category, so income never lands in "Unassigned" in practice.
- `SankeyMapperTest` checks that graph totals equal the card totals in every mode.

## `:sankey` module

An app-agnostic library under package `com.iltermon.sankey`. It has no Room, app or Material3
dependency. API: two lists of `SankeyEntry(id, label, amount, color?)`. The library derives the
ribbons by proportional allocation and adds a synthetic surplus/deficit node ("Unspent" / "Not
covered"). Pure geometry lives in `SankeyLayout.kt` (JVM-tested); rendering and tap-to-highlight
live in `SankeyDiagram.kt`. Original design: `docs/sankey-graph-module-plan.md`.
