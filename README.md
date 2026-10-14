# ExpenseLens

An Android expense tracker built with modern Android tooling.

## Features
- Track income & expenses across multiple **accounts**
- Organize spending with **categories** and **counterparties**
- **Recurring transactions** generated from templates
- **Analytics** dashboard with spending breakdowns (including a Sankey view)
- Offline-first local storage

## Tech stack
- **Kotlin** + **Jetpack Compose** (UI)
- **Room** for persistence, with schema migrations
- MVVM architecture (`ViewModel` + repository)
- Instrumented tests (including a data-migration test)

## Build & run
```bash
./gradlew assembleDebug
```
Or open the project in Android Studio and run the `app` configuration.

## Screenshots
| Expenses | Upcoming recurring | Add expense |
|:---:|:---:|:---:|
| <img src="docs/screenshots/expenses-paid.jpeg" width="220" alt="Expenses list with paid items"> | <img src="docs/screenshots/expenses-recurring.jpeg" width="220" alt="Expenses list showing an upcoming recurring payment"> | <img src="docs/screenshots/add-expense-one-time.jpeg" width="220" alt="Add a one-time expense"> |

| Recurring expense | Analytics | Sankey cash flow |
|:---:|:---:|:---:|
| <img src="docs/screenshots/add-expense-recurring.jpeg" width="220" alt="Add a recurring expense"> | <img src="docs/screenshots/analytics-categories.jpeg" width="220" alt="Analytics breakdown by category (dark theme)"> | <img src="docs/screenshots/analytics-sankey.jpeg" width="220" alt="Sankey cash-flow chart (dark theme)"> |
