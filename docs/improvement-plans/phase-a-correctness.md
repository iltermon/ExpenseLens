# Phase A — Correctness Hardening

Small, high-value, low-risk fixes plus the unit tests that make Phase C safe to attempt.
Everything here is behavior-preserving for well-formed data; the changes only alter what happens
on *malformed* data (skip instead of crash) and on *conflicting* inserts (preserve instead of
clobber).

## A1. Defensive date parsing (HIGH)

**Problem:** a single malformed `date` string anywhere in the DB throws `DateTimeParseException`
inside a flow `map`, killing the whole transactions/analytics screen (the exception propagates
through the `combine`/`stateIn` chain).

**Where:**
- `ExpenseLensViewModel.kt:143` — `filteredTransactions` maps `LocalDate.parse(transaction.date)`
- `ExpenseLensViewModel.kt:284` — `analyticsFilteredTransactions`, same pattern
- `Recurrence.kt:18-19` — `LocalDate.parse(startDate)` / `endDate` on template expansion
- Auto-pay init block (`ExpenseLensViewModel.kt:~97`) parses template dates too

**Change:** introduce one helper (e.g. in `Recurrence.kt` or a small `Dates.kt`):

```kotlin
internal fun parseDateOrNull(s: String): LocalDate? =
    runCatching { LocalDate.parse(s) }.getOrNull()
```

Use it at every parse site; `mapNotNull`/skip rows whose date fails to parse. A skipped row is
invisible but recoverable; a crash loses the whole screen. Optionally log the bad value.

## A2. `OnConflictStrategy.IGNORE` for the auto-pay insert (MED)

**Problem:** `TransactionDao.kt:26` uses `REPLACE` while `Transaction` has a unique index on
`(templateId, date)`. If the in-memory `existingKeys` guard in the auto-pay collector ever misses
(process death mid-run, race), the generated insert silently *deletes and re-inserts* a
user-edited occurrence — wiping their edits to amount/isPaid. `REPLACE` also changes the row id.

**Change:** add a dedicated `@Insert(onConflict = OnConflictStrategy.IGNORE)` method (e.g.
`insertGeneratedTransaction`) and use it from the auto-pay path only. Keep the existing insert for
user-initiated saves (which never hit the unique index). This mirrors the rationale already
documented in `CounterpartyDao.kt:11`.

## A3. Normalize `frequencyUnit` "Monthly" → "Month" (MED)

**Problem:** the entity default and two migration defaults persist `"Monthly"`
(`RecurringTemplate.kt:17`, `ExpenseLensDatabase.kt:55,87`), but every consumer matches only
`"Day"/"Week"/"Month"/"Year"` (`Recurrence.kt:26-32,46-52`, `LocalizedLabels.kt`,
`RecurringForm.kt:48`). Legacy `"Monthly"` rows work only via `else → Month` fallbacks and render
as raw untranslated "Monthly" in the UI.

**Change:**
1. New Room migration: `UPDATE recurring_templates SET frequencyUnit = 'Month' WHERE frequencyUnit = 'Monthly'`.
2. Change the entity default to `"Month"`.
3. Leave the `else` fallbacks in place as a final safety net, but they should no longer be reachable.

## A4. Unit tests for the recurrence engine (HIGH)

**Problem:** the riskiest logic in the app has zero coverage — only the template
`ExampleUnitTest` exists. These tests are the prerequisite safety net for Phase C.

**Change:** new `app/src/test/java/com/iltermon/expenselens/RecurrenceTest.kt` covering
`occurrencesInRange` (pure JVM function, no Android deps):
- Monthly from Jan 31: expect Jan 31 → Feb 28 (or 29 in leap year) → Mar 31 (clamping self-corrects).
- Interval > 1 (e.g. every 3 months, every 2 weeks): correct stride, no drift.
- `endDate` capping: occurrences stop at the cap; open-ended (`endDate = null`) keeps going.
- Range edges: occurrence exactly on range start/end is included; empty range yields empty list.
- Daily/weekly/yearly units.
- Malformed `startDate` (after A1): yields empty list instead of throwing.

Also test the dedup rule if extractable: given a template occurrence and a transaction with the
same `(templateId, date)`, the occurrence is hidden (logic currently at
`ExpenseLensViewModel.kt:223-224` — full coverage may need to wait for the Phase C extraction;
test what is reachable now).

Run with `./gradlew test` (JAVA_HOME → JetBrains Runtime, see project notes).

## A5. Dead code removal (LOW)

- `TransactionDao.getTransactionsByMonth` + `ExpenseLensRepository.getTransactionsByMonth` — never
  called (the intended SQL push-down is unused; Phase C revisits push-down properly).
- `AppSettingDao.clear()` (`AppSetting.kt:29-30`) — never called.
- Stray `@OptIn(ExperimentalCoroutinesApi::class)` on `allTemplates`
  (`ExpenseLensViewModel.kt:162`) — plain `stateIn`, no experimental API used.

## Verification

1. `./gradlew test` — new `RecurrenceTest` green.
2. `./gradlew assembleDebug lint` — green.
3. Manual: insert a deliberately malformed date via the debug importer or sqlite, confirm the
   Expenses screen renders (skipping that row) rather than crashing.
4. Manual: edit an auto-generated transaction's amount, force a fresh app start (auto-pay re-run),
   confirm the edit survives.
