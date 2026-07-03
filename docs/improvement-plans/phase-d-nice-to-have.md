# Phase D — Nice-to-Haves (deferred)

Individually optional; each stands alone. Ordered by value density. All involve schema or
API-surface changes, so each wants its own branch + manual test pass.

## D1. Foreign keys, indices, and delete cleanup (MED)

**Problem:** `accountId`, `counterpartyId`, `templateId` are nullable `Int?` pseudo-FKs with no
`@ForeignKey` and no index (except the composite `(templateId, date)` unique). Consequences:
- `AccountDao` only has `@Delete` — deleting an account leaves `transactions.accountId` dangling
  (rows silently vanish from per-account analytics at `AnalyticsScreen.kt:91` but stay in totals).
- Counterparty deletes DO clean up (`CounterpartyDao.kt:33-37` `clearRefs` + repository
  transaction) — the pattern exists, it just wasn't applied to accounts.
- Categories are referenced by **name string** (`Transaction.category`), so rename/delete orphans
  rows invisibly.

**Change:**
1. Migration adding `@ForeignKey(onDelete = SET_NULL)` on `accountId`/`counterpartyId`
   (+ `@Index` on each FK column; Room requires table-recreate for adding FKs — follow the existing
   recreate-style migrations in `ExpenseLensDatabase.kt`).
2. `templateId`: `SET_NULL` on template delete (already handled in `deleteSeries`? verify — keep
   whichever behavior `deleteSeries` intends).
3. Account delete: mirror the counterparty pattern (repository `withTransaction`: clear refs, then
   delete) — or rely on the FK once added.
4. Categories: bigger decision — either migrate to `categoryId` FK (schema churn, touches forms and
   analytics) or accept name-referencing but add rename-propagation (`UPDATE transactions SET
   category = :new WHERE category = :old` inside the category-rename path). The propagation option
   is 10% of the work and removes 90% of the pain. **Recommended: propagation.**
5. Enable `exportSchema = true` and check in the schema dir — prerequisite for migration tests.

## D2. Stable enum keys instead of English display strings in DB (MED)

**Problem:** `RecurringTemplate.frequencyUnit` stores `"Day"/"Week"/"Month"/"Year"` and
`Account.type` stores `"Debit"/"Credit Card"/...` — English display text as data. Localization
works only because `LocalizedLabels` maps these exact strings; any typo or vocabulary drift (see
the "Monthly" incident, Phase A3) silently degrades.

**Change:**
- `enum class FrequencyUnit { DAY, WEEK, MONTH, YEAR }` and `enum class AccountType { DEBIT,
  CREDIT_CARD, INVESTMENT, CASH, SAVINGS }` with Room `TypeConverter`s storing stable lowercase
  keys (`"day"`, `"credit_card"`).
- Migration mapping existing values; `LocalizedLabels` switches to `when(enum)` — exhaustive, so
  the compiler catches gaps (which is the real win).
- Touches: `Recurrence.kt`, `RecurringForm.kt` (`frequencyUnits` list), `SettingsScreen.kt`
  (`accountTypes` list), `DataImporter`, both `strings.xml` untouched (labels already exist).

## D3. Type-safe navigation (LOW-MED)

**Problem:** `AppNavigation.kt` concatenates route strings
(`navigate("${Routes.EDIT_TRANSACTION}/$id")` at :115-116, :126-127) and hand-parses args
(`entry.arguments?.getInt("id") ?: return@composable` at :149, :160). Typos fail at runtime.

**Change:** Navigation Compose 2.8+ type-safe routes — `@Serializable data class
EditTransaction(val id: Int)` etc., `navController.navigate(EditTransaction(id))`,
`entry.toRoute<EditTransaction>()`. Needs the `kotlinx-serialization` plugin + navigation version
bump. Small app (a handful of routes), so this is an hour's work; the value is compile-time route
safety for everything Phase C4 touches — consider folding into C4 if done around the same time.

## D4. `ui/` sub-packages (LOW)

22 non-theme files flat in `ui/`. After Phase B's moves, restructure:

```
ui/
  screens/     Expenses, Income, Analytics, Settings, Add*, Edit*
  forms/       OneTimeTransactionForm, RecurringForm, TransactionCoreFields, TransactionFormState
  components/  DatePickerField, SectionHeader, ExpenseItemCard, SummaryBar, CounterpartyField,
               SwipeToRevealRow, BackButton, TabScreenShell
  common/      CurrencyFormat, LocalizedLabels, LocaleManager
  dev/         (existing; debug-gated per Phase B2)
```

Pure package-move refactor (IDE-driven, no logic). Do it *last* — after C4 removes the duplicate
screens — so files are only moved once. The boundary then self-enforces the shared-vs-screen
convention.

## D5. Small leftovers

- Split settings persistence is fine as-is (locale must be readable in `attachBaseContext` before
  the DB exists) — just add a comment in `LocaleManager.kt` documenting *why* it differs from the
  Room-backed currency setting.
- Wrap `Repository.clearAll()` in `withContext(Dispatchers.IO)` internally so future callers can't
  block the main thread.
- Migrate the six remaining deprecated no-arg `Modifier.menuAnchor()` calls in
  `SettingsScreen.kt` (:360, :537, :594, :703, :719, :774) to the `ExposedDropdownMenuAnchorType`
  overload, matching the forms.

## Verification

Per item: `./gradlew test assembleDebug lint`. For D1/D2 (migrations): install the pre-migration
APK, create data touching every affected column, upgrade to the post-migration build, verify data
integrity. Room migration tests become possible once `exportSchema = true` (D1.5) is in.
