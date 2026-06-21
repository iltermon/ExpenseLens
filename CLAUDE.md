# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run a single test class
./gradlew test --tests "com.iltermon.expenselens.ExampleUnitTest"

# Lint
./gradlew lint

# Clean build
./gradlew clean assembleDebug
```

## Architecture

**Stack:** Jetpack Compose + Room + Navigation Compose + ViewModel + Kotlin Coroutines/Flow

**Pattern:** MVVM. Data flows Room → Repository → ViewModel (StateFlow) → Composable UI.

### Data layer (`data/`)

Two Room entities:
- `Transaction` — a concrete payment record (`isPaid`, `isExpense`, `date` as ISO string `YYYY-MM-DD`)
- `RecurringTemplate` — a repeating entry active between `startMonth`/`endMonth` (format `YYYY-MM`; `endMonth = null` means open-ended)

`ExpenseLensRepository` is a thin wrapper over the two DAOs. `ExpenseLensDatabase` is the Room singleton.

### UI layer (`ui/`)

`ExpenseLensViewModel` is the single ViewModel for the whole app. It combines `filteredTransactions` and `activeRecurring` into `expenseItems: StateFlow<List<ExpenseItem>>`. `ExpenseItem` is the UI-facing model that unifies both sources — a recurring template that hasn't been paid yet appears as an `ExpenseItem` with `isRecurring = true` and `transactionId = null`. Marking it paid via `togglePaid()` converts it into a real `Transaction` in the database.

`AppNavigation.kt` owns all routes. Currently two screens:
- `expenses` → `ExpensesScreen`
- `add_transaction` → `AddTransactionScreen`

### Key invariants

- Dates stored as `LocalDate.toString()` (`YYYY-MM-DD`); month keys as `YearMonth.toString()` (`YYYY-MM`).
- Recurring deduplication in the ViewModel: a template is hidden for a period if a transaction with the same description already exists in that month (string prefix match on date).
- `enableEdgeToEdge()` is active; bottom UI must use `navigationBarsPadding()` to avoid overlapping the system nav bar.
- `minSdk = 26`, so `java.time` APIs are available without desugaring.
