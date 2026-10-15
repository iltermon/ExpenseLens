# UI layer (`ui/`)

## Packages

| Package | Contents |
|---|---|
| `ui/` | `ExpenseLensViewModel` (the single ViewModel; also defines `ExpenseItem`, `CounterpartyChoice`, `ImportStatus`) |
| `ui/navigation/` | `AppNavigation.kt`: string `Routes` and the NavHost. This is the authoritative screen list. |
| `ui/transactions/` | Expenses/Income list screens, Add/Edit screens, `OneTimeTransactionForm`, `RecurringForm`, `TransactionCoreFields`, `TransactionFormState`, filtering/sorting, unsaved-changes guard |
| `ui/templates/` | Templates list + template edit screen |
| `ui/settings/` | Settings plus accounts/categories/counterparties management |
| `ui/analytics/` | `AnalyticsScreen` (see [analytics.md](analytics.md)) |
| `ui/components/` | Shared composables (`ExpenseItemRow`, `SwipeToRevealRow`, `TabScreenShell`/`SummaryBar`, `FilterBottomSheet`, ...) |
| `ui/util/` | Currency formatting, `LocaleManager`, `LocalizedLabels`, `SankeyMapper` |
| `ui/dev/` | Excel importer (dev only) |

**Convention:** shared or generic composables belong in `ui/components/`, never in a
screen-specific file. Some are still in the wrong place: `DatePickerField` is in `RecurringForm.kt`,
and `SectionHeader` and `ExpenseItemCard` are in `ExpensesScreen.kt` (#29 B1). Move them when you
touch them. `UnsavedChangesDialog` lives in `transactions/UnsavedChanges.kt` but the settings
screens use it too.

## ViewModel data flow

- `expenseItems` = one `combine(allTransactions, allTemplates, _dateRange)` that filters to the
  range and calls `mergeItems`. Range filtering must stay **inside** that combine: a separately
  filtered upstream flow lags a period change by one emission, so the new period's occurrences get
  projected against the old period's payments, and recurring rows flash for a frame.
- `expensesTabItems` / `incomeTabItems` apply per-tab filter and sort state
  (`TransactionItemFiltering.kt`) **after** the merge.
- Analytics has its own `analyticsDateRange`. MONTH mode shares the global month; YEAR mode is
  Analytics-only.
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
