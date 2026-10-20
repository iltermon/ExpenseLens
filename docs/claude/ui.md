# UI layer (`ui/`)

## Packages

| Package | Contents |
|---|---|
| `ui/` | `ExpenseLensViewModel` (lists, tabs, CRUD), `PeriodSelection` (shared month/range), `DateRange`, `ExpenseItems.kt` (`ExpenseItem`, pure `mergeItems`/`inRange`) |
| `ui/navigation/` | `AppNavigation.kt`: string `Routes` and the NavHost. This is the authoritative screen list. |
| `ui/transactions/` | Expenses/Income list screens, Add/Edit screens, `OneTimeTransactionForm`, `RecurringForm`, `TransactionCoreFields`, `TransactionFormState`, filtering/sorting, unsaved-changes guard |
| `ui/templates/` | Templates list + template edit screen |
| `ui/settings/` | Settings plus accounts/categories/counterparties management |
| `ui/analytics/` | `AnalyticsScreen`, `AnalyticsViewModel`, pure `AnalyticsCalculations.kt` (see [analytics.md](analytics.md)) |
| `ui/components/` | Shared composables (`ExpenseItemRow`, `SwipeToRevealRow`, `TabScreenShell`/`SummaryBar`, `FilterBottomSheet`, ...) |
| `ui/util/` | Currency formatting, `LocaleManager`, `LocalizedLabels`, `SankeyMapper` |
| `ui/dev/` | Excel importer + `ImportViewModel` (kept in release builds) |

**File organisation: one concept per file** (Kotlin convention, not one class per file):
1. A file is named after its main type; everything else in it exists only to serve that type
   (e.g. `ImportStatus` lives in `ImportViewModel.kt`, result types in `AnalyticsCalculations.kt`).
2. A type used **across several features** gets its own file (e.g. `DateRange.kt`,
   `data/CounterpartyChoice.kt`), so it can be found without knowing where it was first written.
3. Split a file when it passes ~300–400 lines or starts covering a second topic.

**Convention:** shared or generic composables belong in `ui/components/`, never in a
screen-specific file. Some are still in the wrong place: `DatePickerField` is in `RecurringForm.kt`,
and `SectionHeader` and `ExpenseItemCard` are in `ExpensesScreen.kt` (#29 B1). Move them when you
touch them. `UnsavedChangesDialog` lives in `transactions/UnsavedChanges.kt` but the settings
screens use it too.

## ViewModels

Three ViewModels, all created in `MainActivity` (`by viewModels`) and passed down through
`AppNavigation`; they share one lifetime (the activity's ViewModelStore):

- `ExpenseLensViewModel`: entity lists, the shared `period`, Expenses/Income tab items with filter
  and sort, save/delete pass-throughs, `togglePaid`, `refreshAutoPayments` (called from `onResume`).
- `AnalyticsViewModel(repository, period)`: gets the **same** `PeriodSelection` instance, so month
  changes stay in sync with the Expenses/Income tabs. Year mode is its own state.
- `ImportViewModel`: Excel import and "clear transactions" for Settings. Its `busy` flag and the main
  one both drive the blocking overlay in `AppNavigation`.

Every ViewModel StateFlow uses `stateInWhileSubscribed(viewModelScope, initial)` (`StateFlowSharing.kt`,
one shared `SUBSCRIBE_TIMEOUT_MS`). Within a ViewModel, read each table **once** into a StateFlow and
derive filtered lists and id→name maps from it (`allCategories.map { … }`), instead of calling
`repository.getAll…()` again.

Counterparty resolution for saves lives in the repository (`saveTransaction(t, choice)` etc.), not
in a ViewModel. `CounterpartyChoice` is in `data/` for that reason.

## Data flow

- `expenseItems` = one `combine(allTransactions, allTemplates, period.dateRange)` that filters to the
  range and calls `mergeItems`. Range filtering must stay **inside** that combine: a separately
  filtered upstream flow lags a period change by one emission, so the new period's occurrences get
  projected against the old period's payments, and recurring rows flash for a frame.
- `expensesTabItems` / `incomeTabItems` apply per-tab filter and sort state
  (`TransactionItemFiltering.kt`) **after** the merge.
- `AnalyticsViewModel` derives its own range: MONTH mode uses the shared `PeriodSelection` range;
  YEAR mode is Analytics-only.
- `ExpenseItem` is the UI model that unifies transactions and projected occurrences
  (see [recurring.md](recurring.md)).

## Forms

- `TransactionFormState` holds the fields shared by the one-time and recurring forms, so values
  survive switching tabs.
- Saves go through `CounterpartySaveController`. Typing a new counterparty name creates the
  counterparty; picking an existing one whose defaults differ prompts "update defaults?".
- `rememberFormBackGate` / `rememberUnsavedChangesBackGuard` handle the unsaved-changes prompt on back.
- Category pickers: `active*Categories` for new transactions, unfiltered lists for edit screens.
- **Trimming:** `coreFieldsValid` returns the trimmed `description` (and parses a trimmed amount);
  forms must save `validation.description`, not `shared.description`. Settings dialogs trim names,
  and `counterpartyChoiceFor` trims the counterparty name.
- The Add screens hoist one `date` shared by both tabs: the one-time date becomes the recurring
  start date and vice versa (`onDateChange` / `onStartDateChange`).

## Display conventions

- Long single-line texts in cards use `maxLines = 1` + `TextOverflow.Ellipsis`.
- `enableEdgeToEdge()` is on, so bottom UI needs `navigationBarsPadding()`.
- Every new string goes in both `values/strings.xml` and `values-tr/strings.xml`.
- Stored English enum-like values (account type, frequency unit) are localized for display through
  `LocalizedLabels`.
