# Phase B — Convention & Shipping Fixes

Mechanical changes: no logic touched, no behavior change for users of release-worthy features.
Independent of Phases A/C/D — can be done any time.

## B1. Move shared composables out of screen files (HIGH)

**Convention:** generic/shared composables must not live in screen-specific files (established
during the `TransactionCoreFields` extraction; see also `TransactionFormState.kt`,
`CounterpartyField.kt`, `TransactionCoreFields.kt` as the pattern to follow — one shared concern
per file in `ui/`).

**Violations found (definition site → foreign consumers):**

| Composable | Defined in | Also used by |
|---|---|---|
| `DatePickerField` | `RecurringForm.kt:191` | `OneTimeTransactionForm.kt` |
| `SectionHeader` | `ExpensesScreen.kt:110` | `IncomeScreen.kt:54,63,73` |
| `ExpenseItemCard` | `ExpensesScreen.kt:132` | `ExpenseItemRow.kt:39` |
| `SummaryBar` | `TabScreenShell.kt` | `AnalyticsScreen.kt:102` |

**Change:** move each into its own (or a small grouped) file in `ui/` — e.g. `DatePickerField.kt`,
`SectionHeader.kt`, `ExpenseItemCard.kt`, `SummaryBar.kt`. Pure cut-and-paste plus imports; keep
`internal` visibility.

**Naming collision:** a second, unrelated private `SectionHeader(title, onAdd)` exists at
`SettingsScreen.kt:374`. Rename it `SettingsSectionHeader` so the shared one is unambiguous.

## B2. Gate the dev Excel importer out of release builds (MED)

**Problem:** `ui/dev/DataImporter.kt` + `ui/dev/XlsxReader.kt` (~342 lines of prototype migration
code) are reachable in release builds via an ungated Settings button
(`SettingsScreen.kt:159`, `settings_import_excel`) → `ExpenseLensViewModel.importFromUri`.
Release has `isMinifyEnabled = false` so nothing strips it. Contrast: the DEBUG on-screen marker
*is* correctly gated at `MainActivity.kt:85`.

(Note: no heavyweight deps involved — `XlsxReader` is a dependency-free `java.util.zip` reader —
so this is about shipping prototype code paths, not APK size.)

**Change (pick one):**
- *Minimal:* wrap the Settings import button (and the import/clear rows around it) in
  `if (BuildConfig.DEBUG)`, and guard `importFromUri` with the same check.
- *Cleaner:* move `ui/dev/` to `app/src/debug/java/...` (debug source set) and provide a no-op or
  absent hook in release. Requires splitting the VM's import functions behind an interface —
  slightly more work, consider deferring to the Phase C ViewModel split where import becomes its
  own use-case anyway.

Recommendation: do the minimal `BuildConfig.DEBUG` gate now; revisit source-set separation in
Phase C.

## B3. Reconcile importer account-type vocabulary (MED)

**Problem:** `DataImporter.kt:19-27` writes account types `"Bank"`, `"Digital"`, `"Brokerage"`
(default `"Bank"`), but `LocalizedLabels.accountTypeLabel` (`LocalizedLabels.kt:15-22`) only maps
the five Settings-created values (`Debit`, `Credit Card`, `Investment`, `Cash`, `Savings`).
Imported accounts therefore display raw English regardless of locale, forever.

**Change (pick one):**
- Map importer types onto the canonical five at import time (`Bank→Debit`, `Digital→Debit` or
  `Cash`, `Brokerage→Investment`) — keeps one vocabulary. **Recommended.**
- Or add the three missing keys to `accountTypeLabel` + both `strings.xml` files.

Existing already-imported rows: if any device has imported data, add a small one-time migration or
leave as-is (dev-only feature; likely only Ilter's own device — a re-import fixes it).

## Verification

1. `./gradlew assembleDebug lint` — green; no unresolved references after the moves.
2. Grep: no composable defined in a `*Screen.kt`/form file is referenced from another file.
3. Debug build: Settings still shows the import button; import works.
4. Release build (`./gradlew assembleRelease`): Settings shows no import button.
5. Import a spreadsheet in debug, switch language to Turkish, confirm account types localize.
